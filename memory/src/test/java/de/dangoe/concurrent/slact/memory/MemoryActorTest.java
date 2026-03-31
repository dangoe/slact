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
@DisplayName("MemoryActor")
public class MemoryActorTest {

  private static final float[] EMBEDDING = new float[]{0.1f, 0.2f, 0.3f};

  @Nested
  @DisplayName("given a WriteMemory command")
  class GivenAWriteMemoryCommand {

    @Test
    @DisplayName("should respond with Written containing the stored memory ID")
    void shouldRespondWithWritten(final @NotNull SlactTestContainer container) throws Exception {

      final var store = mock(MemoryStore.class);
      when(store.save(any())).thenAnswer(inv -> {
        final Memory saved = inv.getArgument(0);
        return RichFuture.of(CompletableFuture.completedFuture(saved.id()));
      });

      final var memoryActor = container.spawn("memory-actor", () -> new MemoryActor(store));

      container.awaitReady(memoryActor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.WriteMemory("hello world", EMBEDDING, Map.of()))
          .ofType(MemoryResponse.Written.class).from(memoryActor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().memoryId()).isNotBlank();
    }
  }

  @Nested
  @DisplayName("given a QueryMemory command")
  class GivenAQueryMemoryCommand {

    @Test
    @DisplayName("should respond with QueryResult containing matched entries")
    void shouldRespondWithQueryResult(final @NotNull SlactTestContainer container)
        throws Exception {

      final var memory = Memory.of("hello", EMBEDDING, Map.of());
      final var entry = new MemoryEntry(memory, 0.95);

      final var store = mock(MemoryStore.class);
      when(store.query(any())).thenReturn(
          RichFuture.of(CompletableFuture.completedFuture(List.of(entry))));

      final var memoryActor = container.spawn("memory-actor", () -> new MemoryActor(store));

      container.awaitReady(memoryActor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.QueryMemory(EMBEDDING, 5))
          .ofType(MemoryResponse.QueryResult.class).from(memoryActor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      final var result = eventualResponse.get();
      assertThat(result.entries()).hasSize(1);
      assertThat(result.entries().get(0).score()).isEqualTo(0.95);
      assertThat(result.entries().get(0).memory().content()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("given a store that fails")
  class GivenAStoreThatFails {

    @Test
    @DisplayName("should respond with Failure when save throws")
    void shouldRespondWithFailureOnSaveError(final @NotNull SlactTestContainer container)
        throws Exception {

      final var store = mock(MemoryStore.class);
      when(store.save(any())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(
              new RuntimeException("simulated save failure"))));

      final var memoryActor = container.spawn("memory-actor", () -> new MemoryActor(store));

      container.awaitReady(memoryActor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.WriteMemory("oops", EMBEDDING, Map.of()))
          .ofType(MemoryResponse.Failure.class).from(memoryActor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("simulated save failure");
    }

    @Test
    @DisplayName("should respond with Failure when query throws")
    void shouldRespondWithFailureOnQueryError(final @NotNull SlactTestContainer container)
        throws Exception {

      final var store = mock(MemoryStore.class);
      when(store.query(any())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(
              new RuntimeException("simulated query failure"))));

      final var memoryActor = container.spawn("memory-actor", () -> new MemoryActor(store));

      container.awaitReady(memoryActor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand) new MemoryCommand.QueryMemory(EMBEDDING, 3))
          .ofType(MemoryResponse.Failure.class).from(memoryActor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("simulated query failure");
    }
  }
}
