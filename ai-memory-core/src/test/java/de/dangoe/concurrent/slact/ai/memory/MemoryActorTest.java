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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
@DisplayName("MemoryActor")
class MemoryActorTest {

  private MemoryStrategy stubStrategy() {
    return mock(MemoryStrategy.class);
  }

  // -------------------------------------------------------------------------
  // Memorize
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("given a Memorize command")
  class GivenAMemorizeCommand {

    @Test
    @DisplayName("should respond with Written containing the stored memory ID")
    void shouldRespondWithWritten(final @NotNull SlactTestContainer container) throws Exception {

      final var strategy = stubStrategy();
      final var expectedId = UUID.randomUUID();
      when(strategy.memorize(anyString(), anyMap())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(expectedId)));

      final var actor = container.spawn("memory-actor-memorize", () -> new MemoryActor(strategy));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.Memorize("hello world", Map.of()))
          .ofType(MemoryResponse.Written.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().memoryId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("should respond with Failure when the strategy throws")
    void shouldRespondWithFailureOnStrategyError(final @NotNull SlactTestContainer container)
        throws Exception {

      final var strategy = stubStrategy();
      when(strategy.memorize(anyString(), anyMap())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(
              new RuntimeException("simulated save failure"))));

      final var actor = container.spawn("memory-actor-save-fail", () -> new MemoryActor(strategy));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.Memorize("oops", Map.of()))
          .ofType(MemoryResponse.Failure.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("simulated save failure");
    }
  }

  // -------------------------------------------------------------------------
  // Query
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("given a Query command")
  class GivenAQueryCommand {

    @Test
    @DisplayName("should respond with QueryResult containing matched entries")
    void shouldRespondWithQueryResult(final @NotNull SlactTestContainer container)
        throws Exception {

      final var embedding = new Embedding(new float[]{0.1f, 0.2f, 0.3f});
      final var memory = Memory.of("hello", embedding.values(), Map.of());
      final var entry = new MemoryEntry(memory, new Score(0.95));

      final var strategy = stubStrategy();
      when(strategy.retrieve(anyString(), anyInt())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of(entry))));

      final var actor = container.spawn("memory-actor-query", () -> new MemoryActor(strategy));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.Query("hello", 5))
          .ofType(MemoryResponse.QueryResult.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      final var result = eventualResponse.get();
      assertThat(result.entries()).hasSize(1);
      assertThat(result.entries().get(0).score().value()).isEqualTo(0.95);
      assertThat(result.entries().get(0).memory().content()).isEqualTo("hello");
    }

    @Test
    @DisplayName("should respond with Failure when the strategy throws")
    void shouldRespondWithFailureOnQueryError(final @NotNull SlactTestContainer container)
        throws Exception {

      final var strategy = stubStrategy();
      when(strategy.retrieve(anyString(), anyInt())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(
              new RuntimeException("simulated query failure"))));

      final var actor = container.spawn("memory-actor-query-fail",
          () -> new MemoryActor(strategy));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.Query("something", 3))
          .ofType(MemoryResponse.Failure.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("simulated query failure");
    }
  }

  // -------------------------------------------------------------------------
  // Forget
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("given a Forget command")
  class GivenAForgetCommand {

    @Test
    @DisplayName("should respond with Forgotten containing the memory ID")
    void shouldRespondWithForgotten(final @NotNull SlactTestContainer container) throws Exception {

      final var targetId = UUID.randomUUID();
      final var strategy = stubStrategy();
      when(strategy.delete(targetId)).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(null)));

      final var actor = container.spawn("memory-actor-forget", () -> new MemoryActor(strategy));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.Forget(targetId))
          .ofType(MemoryResponse.Forgotten.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().memoryId()).isEqualTo(targetId);
    }

    @Test
    @DisplayName("should respond with Failure when the strategy throws")
    void shouldRespondWithFailureOnDeleteError(final @NotNull SlactTestContainer container)
        throws Exception {

      final var targetId = UUID.randomUUID();
      final var strategy = stubStrategy();
      when(strategy.delete(targetId)).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(
              new RuntimeException("simulated delete failure"))));

      final var actor = container.spawn("memory-actor-forget-fail",
          () -> new MemoryActor(strategy));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.Forget(targetId))
          .ofType(MemoryResponse.Failure.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("simulated delete failure");
    }
  }
}
