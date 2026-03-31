package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.Actor;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Actor that handles memory read and write operations.
 *
 * <p>Processes {@link MemoryCommand} messages and responds with {@link MemoryResponse} values
 * back to the sender.
 */
public final class MemoryActor extends Actor<MemoryCommand> {

  private final @NotNull MemoryStore store;

  /**
   * Creates a new memory actor.
   *
   * @param store the memory store used to persist and query memories.
   */
  public MemoryActor(final @NotNull MemoryStore store) {
    this.store = Objects.requireNonNull(store, "Memory store must not be null");
  }

  @Override
  public void onMessage(final @NotNull MemoryCommand message) {
    switch (message) {
      case MemoryCommand.WriteMemory cmd -> handleWrite(cmd);
      case MemoryCommand.QueryMemory cmd -> handleQuery(cmd);
    }
  }

  private void handleWrite(final @NotNull MemoryCommand.WriteMemory cmd) {
    try {
      final var memory = Memory.of(cmd.content(), cmd.embedding(), cmd.metadata());
      final var id = store.save(memory).join();
      respondWith(new MemoryResponse.Written(id));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }

  private void handleQuery(final @NotNull MemoryCommand.QueryMemory cmd) {
    try {
      final var query = new MemoryQuery(cmd.embedding(), cmd.maxResults());
      final var entries = store.query(query).join();
      respondWith(new MemoryResponse.QueryResult(entries));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }
}
