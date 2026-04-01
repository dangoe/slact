package de.dangoe.concurrent.slact.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
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

  private static final Embedding EMBEDDING = new Embedding(new float[]{0.1f, 0.2f, 0.3f});

  private MemoryStore stubStoreWithSave() {
    final var store = mock(MemoryStore.class);
    when(store.save(any())).thenAnswer(inv -> {
      final Memory saved = inv.getArgument(0);
      return RichFuture.of(CompletableFuture.completedFuture(saved.id()));
    });
    return store;
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

      final var strategy = mock(MemorizationStrategy.class);
      final var expectedId = UUID.randomUUID();
      when(strategy.memorize(anyString(), anyMap())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(expectedId)));

      final var store = mock(MemoryStore.class);
      final var actor = container.spawn("memory-actor-memorize",
          () -> new MemoryActor(store, strategy));
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

      final var strategy = mock(MemorizationStrategy.class);
      when(strategy.memorize(anyString(), anyMap())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(
              new RuntimeException("simulated save failure"))));

      final var store = mock(MemoryStore.class);
      final var actor = container.spawn("memory-actor-save-fail",
          () -> new MemoryActor(store, strategy));
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

      final var memory = Memory.of("hello", new float[]{0.1f, 0.2f, 0.3f}, Map.of());
      final var entry = new MemoryEntry(memory, new Score(0.95));

      final var store = mock(MemoryStore.class);
      when(store.query(any())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of(entry))));

      final var strategy = mock(MemorizationStrategy.class);
      final var actor = container.spawn("memory-actor-query",
          () -> new MemoryActor(store, strategy));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.Query(EMBEDDING, 5))
          .ofType(MemoryResponse.QueryResult.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      final var result = eventualResponse.get();
      assertThat(result.entries()).hasSize(1);
      assertThat(result.entries().get(0).score().value()).isEqualTo(0.95);
      assertThat(result.entries().get(0).memory().content()).isEqualTo("hello");
    }

    @Test
    @DisplayName("should respond with Failure when query throws")
    void shouldRespondWithFailureOnQueryError(final @NotNull SlactTestContainer container)
        throws Exception {

      final var store = mock(MemoryStore.class);
      when(store.query(any())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(
              new RuntimeException("simulated query failure"))));

      final var strategy = mock(MemorizationStrategy.class);
      final var actor = container.spawn("memory-actor-query-fail",
          () -> new MemoryActor(store, strategy));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.Query(EMBEDDING, 3))
          .ofType(MemoryResponse.Failure.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("simulated query failure");
    }
  }
}
