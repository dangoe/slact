package de.dangoe.concurrent.slact.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.testkit.Constants;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  private MemoryStrategy emptyQueryStrategy() {
    final var strategy = mock(MemoryStrategy.class);
    when(strategy.retrieve(anyString(), anyInt())).thenReturn(
        RichFuture.of(CompletableFuture.completedFuture(List.of())));
    when(strategy.memorize(anyString(), anyMap())).thenReturn(
        RichFuture.of(CompletableFuture.completedFuture(UUID.randomUUID())));
    when(strategy.delete(any())).thenReturn(
        RichFuture.of(CompletableFuture.completedFuture(null)));
    return strategy;
  }

  private MemoryStrategy noOpStrategy() {
    final var strategy = mock(MemoryStrategy.class);
    when(strategy.memorize(anyString(), anyMap())).thenReturn(
        RichFuture.of(CompletableFuture.completedFuture(UUID.randomUUID())));
    when(strategy.retrieve(anyString(), anyInt())).thenReturn(
        RichFuture.of(CompletableFuture.completedFuture(List.of())));
    when(strategy.delete(any())).thenReturn(
        RichFuture.of(CompletableFuture.completedFuture(null)));
    return strategy;
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
          () -> new MemoryActor(emptyQueryStrategy()));
      final var contextMergeActor = container.spawn("context-merge-actor",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator",
          () -> new PromptOrchestratorActor(memoryActor, contextMergeActor,
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
    @DisplayName("should respond with Failure when the memory query fails")
    void shouldRespondWithFailureWhenMemoryQueryFails(final @NotNull SlactTestContainer container)
        throws Exception {

      final var strategy = mock(MemoryStrategy.class);
      when(strategy.retrieve(anyString(), anyInt())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(new RuntimeException("query error"))));

      final var targetModel = mock(TargetModelPort.class);
      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of())));

      final var memoryActor = container.spawn("memory-actor-query-fail",
          () -> new MemoryActor(strategy));
      final var contextMergeActor = container.spawn("context-merge-actor-query-fail",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-query-fail",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-query-fail",
          () -> new PromptOrchestratorActor(memoryActor, contextMergeActor,
              llmCallActor, extractionPort));

      container.awaitReady(memoryActor.path(), contextMergeActor.path(), llmCallActor.path(),
          orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process("hello"))
          .ofType(PromptResponse.Failure.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);
      assertThat(eventualResponse.get().errorMessage()).contains("query error");
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
          () -> new MemoryActor(emptyQueryStrategy()));
      final var contextMergeActor = container.spawn("context-merge-actor-fail",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-fail",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-fail",
          () -> new PromptOrchestratorActor(memoryActor, contextMergeActor,
              llmCallActor, extractionPort));

      container.awaitReady(memoryActor.path(), contextMergeActor.path(), llmCallActor.path(),
          orchestrator.path());

      final var eventualResponse = container.requestResponseTo(
              (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process("fail"))
          .ofType(PromptResponse.Failure.class).from(orchestrator);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).isEqualTo("LLM call failed");
      assertThat(eventualResponse.get().cause().getMessage()).contains("model error");
    }

    @Test
    @DisplayName("should merge retrieved memories into the prompt")
    void shouldMergeRetrievedMemoriesIntoPrompt(final @NotNull SlactTestContainer container) {

      final var memoryEntry = new MemoryEntry(
          Memory.of("user: name=Alice", EMBEDDING.values(), Map.of()), new Score(0.9));

      final var strategy = mock(MemoryStrategy.class);
      when(strategy.retrieve(anyString(), anyInt())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of(memoryEntry))));
      when(strategy.memorize(anyString(), anyMap())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(UUID.randomUUID())));

      final var capturedPrompt = new AtomicReference<String>();
      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenAnswer(inv -> {
        capturedPrompt.set(inv.getArgument(0));
        return RichFuture.of(CompletableFuture.completedFuture("answer"));
      });

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of())));

      final var memoryActor = container.spawn("memory-actor-merge",
          () -> new MemoryActor(strategy));
      final var contextMergeActor = container.spawn("context-merge-actor-merge",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-merge",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-merge",
          () -> new PromptOrchestratorActor(memoryActor, contextMergeActor,
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
          () -> new MemoryActor(emptyQueryStrategy()));
      final var contextMergeActor = container.spawn("context-merge-actor-wait",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-wait",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-wait",
          () -> new PromptOrchestratorActor(memoryActor, contextMergeActor,
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
    void shouldStillRespondWithAnswerWhenExtractionFails(
        final @NotNull SlactTestContainer container) throws Exception {

      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture("mocked answer")));

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(new RuntimeException("extract error"))));

      final var memoryActor = container.spawn("memory-actor-extract-fail",
          () -> new MemoryActor(emptyQueryStrategy()));
      final var contextMergeActor = container.spawn("context-merge-actor-extract-fail",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-extract-fail",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-extract-fail",
          () -> new PromptOrchestratorActor(memoryActor, contextMergeActor,
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
    @DisplayName("should still respond with Answer when memorization strategy fails")
    void shouldStillRespondWithAnswerWhenMemorizationFails(
        final @NotNull SlactTestContainer container) throws Exception {

      final var targetModel = mock(TargetModelPort.class);
      when(targetModel.complete(anyString())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture("mocked answer")));

      final var extractionPort = mock(MemoryExtractionPort.class);
      when(extractionPort.extract(anyString(), anyString())).thenReturn(RichFuture.of(
          CompletableFuture.completedFuture(List.of(new MemoryCandidate("user", "name=Alice")))));

      final var strategy = mock(MemoryStrategy.class);
      when(strategy.retrieve(anyString(), anyInt())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of())));
      when(strategy.memorize(anyString(), anyMap())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(new RuntimeException("memorize error"))));

      final var memoryActor = container.spawn("memory-actor-memorize-fail",
          () -> new MemoryActor(strategy));
      final var contextMergeActor = container.spawn("context-merge-actor-memorize-fail",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor-memorize-fail",
          () -> new LlmCallActor(targetModel));

      final var orchestrator = container.spawn("orchestrator-memorize-fail",
          () -> new PromptOrchestratorActor(memoryActor, contextMergeActor,
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
