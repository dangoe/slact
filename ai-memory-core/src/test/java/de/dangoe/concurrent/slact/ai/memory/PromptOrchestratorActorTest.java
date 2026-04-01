package de.dangoe.concurrent.slact.ai.memory;

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
import java.util.concurrent.atomic.AtomicReference;
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

      final var memoryActor = container.spawn("memory-actor",
          () -> new MemoryActor(emptyMemoryStore()));
      final var contextMergeActor = container.spawn("context-merge-actor",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator",
          () -> new PromptOrchestratorActor(stubEmbeddingPort(), memoryActor, contextMergeActor,
              llmCallActor, extractionPort));

      container.awaitReady(memoryActor.path(), contextMergeActor.path(), llmCallActor.path(),
          orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process("hello"))
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

      final var memoryActor = container.spawn("memory-actor-fail",
          () -> new MemoryActor(emptyMemoryStore()));
      final var contextMergeActor = container.spawn("context-merge-actor-fail",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-fail",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-fail",
          () -> new PromptOrchestratorActor(stubEmbeddingPort(), memoryActor, contextMergeActor,
              llmCallActor, extractionPort));

      container.awaitReady(memoryActor.path(), contextMergeActor.path(), llmCallActor.path(),
          orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process("fail"))
          .ofType(PromptResponse.Failure.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("model error");
    }

    @Test
    @DisplayName("should merge retrieved memories into the prompt")
    void shouldMergeRetrievedMemoriesIntoPrompt(final @NotNull SlactTestContainer container) {

      final var memoryEntry = new MemoryEntry(
          Memory.of("user: name=Alice", EMBEDDING.values(), Map.of()), new Score(0.9));

      final var store = mock(MemoryStore.class);
      when(store.query(any())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of(memoryEntry))));
      when(store.save(any())).thenAnswer(inv -> {
        final Memory saved = inv.getArgument(0);
        return RichFuture.of(CompletableFuture.completedFuture(saved.id()));
      });

      final var capturedPrompt = new AtomicReference<String>();
      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenAnswer(inv -> {
        capturedPrompt.set(inv.getArgument(0));
        return RichFuture.of(CompletableFuture.completedFuture("answer"));
      });

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of())));

      final var memoryActor = container.spawn("memory-actor-merge", () -> new MemoryActor(store));
      final var contextMergeActor = container.spawn("context-merge-actor-merge",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-merge",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-merge",
          () -> new PromptOrchestratorActor(stubEmbeddingPort(), memoryActor, contextMergeActor,
              llmCallActor, extractionPort));

      container.awaitReady(memoryActor.path(), contextMergeActor.path(), llmCallActor.path(),
          orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
          (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process(
              "Who am I?")).ofType(PromptResponse.Answer.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(capturedPrompt.get()).contains("user: name=Alice");
      assertThat(capturedPrompt.get()).contains("Who am I?");
    }

    @Test
    @DisplayName("should respond before memory extraction completion")
    void shouldRespondBeforeMemoryExtractionCompletion(final @NotNull SlactTestContainer container)
        throws Exception {

      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture("mocked answer")));

      final var extractionFuture = new CompletableFuture<List<MemoryCandidate>>();
      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(extractionFuture));

      final var memoryActor = container.spawn("memory-actor-wait",
          () -> new MemoryActor(emptyMemoryStore()));
      final var contextMergeActor = container.spawn("context-merge-actor-wait",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-wait",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-wait",
          () -> new PromptOrchestratorActor(stubEmbeddingPort(), memoryActor, contextMergeActor,
              llmCallActor, extractionPort));

      container.awaitReady(memoryActor.path(), contextMergeActor.path(), llmCallActor.path(),
          orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process("hello"))
          .ofType(PromptResponse.Answer.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);
      assertThat(eventualResponse.get().text()).isEqualTo("mocked answer");
      assertThat(extractionFuture.isDone()).isFalse();
    }

    @Test
    @DisplayName("should still respond with Answer when memory extraction fails asynchronously")
    void shouldStillRespondWithAnswerWhenExtractionFails(final @NotNull SlactTestContainer container)
        throws Exception {

      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture("mocked answer")));

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(
              CompletableFuture.failedFuture(new RuntimeException("extract error"))));

      final var memoryActor = container.spawn("memory-actor-extract-fail",
          () -> new MemoryActor(emptyMemoryStore()));
      final var contextMergeActor = container.spawn("context-merge-actor-extract-fail",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-extract-fail",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-extract-fail",
          () -> new PromptOrchestratorActor(stubEmbeddingPort(), memoryActor, contextMergeActor,
              llmCallActor, extractionPort));

      container.awaitReady(memoryActor.path(), contextMergeActor.path(), llmCallActor.path(),
          orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process("hello"))
          .ofType(PromptResponse.Answer.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);
      assertThat(eventualResponse.get().text()).isEqualTo("mocked answer");
    }

    @Test
    @DisplayName("should still respond with Answer when saving extracted memory fails")
    void shouldStillRespondWithAnswerWhenSavingExtractedMemoryFails(
        final @NotNull SlactTestContainer container) throws Exception {

      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture("mocked answer")));

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(RichFuture.of(
          CompletableFuture.completedFuture(List.of(new MemoryCandidate("user", "name=Alice")))));

      final var store = mock(MemoryStore.class);
      when(store.query(any())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of())));
      when(store.save(any())).thenReturn(
          RichFuture.of(
              CompletableFuture.failedFuture(new RuntimeException("save error"))));

      final var memoryActor = container.spawn("memory-actor-save-fail",
          () -> new MemoryActor(store));
      final var contextMergeActor = container.spawn("context-merge-actor-save-fail",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-save-fail",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-save-fail",
          () -> new PromptOrchestratorActor(stubEmbeddingPort(), memoryActor, contextMergeActor,
              llmCallActor, extractionPort));

      container.awaitReady(memoryActor.path(), contextMergeActor.path(), llmCallActor.path(),
          orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process("hello"))
          .ofType(PromptResponse.Answer.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);
      assertThat(eventualResponse.get().text()).isEqualTo("mocked answer");
    }
  }
}
