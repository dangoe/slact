package de.dangoe.concurrent.slact.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
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
@DisplayName("MemoryRetrievalActor")
class MemoryRetrievalActorTest {

  private static final Embedding EMBEDDING = new Embedding(new float[]{0.1f, 0.2f, 0.3f});

  @Nested
  @DisplayName("given a QueryMemory command")
  class GivenAQueryMemoryCommand {

    @Test
    @DisplayName("should respond with QueryResult containing matched entries")
    void shouldRespondWithQueryResult(final @NotNull SlactTestContainer container)
        throws Exception {

      final var memory = Memory.of("hello", new float[]{0.1f, 0.2f, 0.3f}, Map.of());
      final var entry = new MemoryEntry(memory, new Score(0.95));

      final var store = mock(MemoryStore.class);
      when(store.query(any())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of(entry))));

      final var actor = container.spawn("retrieval-actor",
          () -> new MemoryRetrievalActor(store));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand.QueryMemory) new MemoryCommand.QueryMemory(EMBEDDING, 5))
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

      final var actor = container.spawn("retrieval-actor",
          () -> new MemoryRetrievalActor(store));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand.QueryMemory) new MemoryCommand.QueryMemory(EMBEDDING, 3))
          .ofType(MemoryResponse.Failure.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("simulated query failure");
    }
  }
}
