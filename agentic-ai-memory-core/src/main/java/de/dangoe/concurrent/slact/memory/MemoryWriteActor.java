package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.Actor;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Actor responsible for handling memory write commands.
 */
public final class MemoryWriteActor extends Actor<MemoryCommand.WriteMemory> {

  private final @NotNull MemoryStore store;

  /**
   * Creates a new MemoryWriteActor with the given memory store.
   *
   * @param store the memory store to write to, must not be {@code null}.
   */
  public MemoryWriteActor(final @NotNull MemoryStore store) {
    this.store = Objects.requireNonNull(store, "Memory store must not be null");
  }

  @Override
  public void onMessage(final @NotNull MemoryCommand.WriteMemory message) {
    try {
      final var memory = Memory.of(message.content(), message.embedding(), message.metadata());
      final var id = store.save(memory).join();
      respondWith(new MemoryResponse.Written(id));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }
}
