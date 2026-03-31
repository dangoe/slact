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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
@DisplayName("MemoryWriteActor")
class MemoryWriteActorTest {

  private static final Embedding EMBEDDING = new Embedding(new float[]{0.1f, 0.2f, 0.3f});

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

      final var actor = container.spawn("write-actor", () -> new MemoryWriteActor(store));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand.WriteMemory) new MemoryCommand.WriteMemory(
                  "hello world", EMBEDDING, Map.of()))
          .ofType(MemoryResponse.Written.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().memoryId()).isNotNull();
    }

    @Test
    @DisplayName("should respond with Failure when save throws")
    void shouldRespondWithFailureOnSaveError(final @NotNull SlactTestContainer container)
        throws Exception {

      final var store = mock(MemoryStore.class);
      when(store.save(any())).thenReturn(
          RichFuture.of(CompletableFuture.failedFuture(
              new RuntimeException("simulated save failure"))));

      final var actor = container.spawn("write-actor", () -> new MemoryWriteActor(store));
      container.awaitReady(actor.path());

      final var eventualResponse = container.requestResponseTo(
              (MemoryCommand.WriteMemory) new MemoryCommand.WriteMemory(
                  "oops", EMBEDDING, Map.of()))
          .ofType(MemoryResponse.Failure.class).from(actor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get().errorMessage()).contains("simulated save failure");
    }
  }
}
