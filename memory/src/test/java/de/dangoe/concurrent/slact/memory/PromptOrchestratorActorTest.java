package de.dangoe.concurrent.slact.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.testkit.Constants;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
@DisplayName("PromptOrchestratorActor")
class PromptOrchestratorActorTest {

  private static final Embedding EMBEDDING = new Embedding(new float[]{0.1f, 0.2f, 0.3f});

  private EmbeddingPort stubEmbeddingPort() {
    final var port = mock(EmbeddingPort.class);
    when(port.embed(anyString())).thenReturn(
        RichFuture.of(CompletableFuture.completedFuture(EMBEDDING)));
    return port;
  }

  private MemoryStore emptyMemoryStore() {
    final var store = mock(MemoryStore.class);
    when(store.query(any())).thenReturn(
        RichFuture.of(CompletableFuture.completedFuture(List.of())));
    when(store.save(any())).thenAnswer(inv -> {
      final Memory saved = inv.getArgument(0);
      return RichFuture.of(CompletableFuture.completedFuture(saved.id()));
    });
    return store;
  }

  @Nested
  @DisplayName("given a Process command")
  class GivenAProcessCommand {

    @Test
    @DisplayName("should respond with Answer containing the target model response")
    void shouldRespondWithAnswer(final @NotNull SlactTestContainer container) throws Exception {

      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture("mocked answer")));

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of())));

      final var writeActor = container.spawn("write-actor",
          () -> new MemoryWriteActor(emptyMemoryStore()));

      final var orchestrator = container.spawn("orchestrator",
          () -> new PromptOrchestratorActor(
              stubEmbeddingPort(), emptyMemoryStore(), targetModel, extractionPort, writeActor));

      container.awaitReady(writeActor.path(), orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process(
                  "hello"))
          .ofType(PromptResponse.Answer.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().text()).isEqualTo("mocked answer");
    }

    @Test
    @DisplayName("should respond with Failure when the target model throws")
    void shouldRespondWithFailureWhenTargetModelThrows(final @NotNull SlactTestContainer container)
        throws Exception {

      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(new RuntimeException("model error"))));

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of())));

      final var writeActor = container.spawn("write-actor-fail",
          () -> new MemoryWriteActor(emptyMemoryStore()));

      final var orchestrator = container.spawn("orchestrator-fail",
          () -> new PromptOrchestratorActor(
              stubEmbeddingPort(), emptyMemoryStore(), targetModel, extractionPort, writeActor));

      container.awaitReady(writeActor.path(), orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process(
                  "fail"))
          .ofType(PromptResponse.Failure.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("model error");
    }

    @Test
    @DisplayName("should merge retrieved memories into the prompt")
    void shouldMergeRetrievedMemoriesIntoPrompt(final @NotNull SlactTestContainer container)
        throws Exception {

      final var memoryEntry = new MemoryEntry(
          Memory.of("user: name=Alice", EMBEDDING.values(), Map.of()), new Score(0.9));

      final var store = mock(MemoryStore.class);
      when(store.query(any())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of(memoryEntry))));
      when(store.save(any())).thenAnswer(inv -> {
        final Memory saved = inv.getArgument(0);
        return RichFuture.of(CompletableFuture.completedFuture(saved.id()));
      });

      final var capturedPrompt = new java.util.concurrent.atomic.AtomicReference<String>();
      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenAnswer(inv -> {
        capturedPrompt.set(inv.getArgument(0));
        return RichFuture.of(CompletableFuture.completedFuture("answer"));
      });

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of())));

      final var writeActor = container.spawn("write-actor-merge",
          () -> new MemoryWriteActor(store));

      final var orchestrator = container.spawn("orchestrator-merge",
          () -> new PromptOrchestratorActor(
              stubEmbeddingPort(), store, targetModel, extractionPort, writeActor));

      container.awaitReady(writeActor.path(), orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process(
                  "Who am I?"))
          .ofType(PromptResponse.Answer.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(capturedPrompt.get()).contains("user: name=Alice");
      assertThat(capturedPrompt.get()).contains("Who am I?");
    }
  }
}
