package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.Actor;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class MemoryRetrievalActor extends Actor<MemoryCommand.QueryMemory> {

  private final @NotNull MemoryStore store;

  public MemoryRetrievalActor(final @NotNull MemoryStore store) {
    this.store = Objects.requireNonNull(store, "Memory store must not be null");
  }

  @Override
  public void onMessage(final @NotNull MemoryCommand.QueryMemory message) {
    try {
      final var query = new MemoryQuery(message.embedding(), message.maxResults());
      final var entries = store.query(query).join();
      respondWith(new MemoryResponse.QueryResult(entries));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }
}
