package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.Actor;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Actor that handles both memory write ({@link MemoryCommand.Memorize}) and read
 * ({@link MemoryCommand.Query}) commands against a {@link MemoryStore}.
 *
 * <p>Write operations are fully delegated to the provided {@link MemorizationStrategy}, which
 * encapsulates all storage-specific details (e.g. embedding computation, deduplication).
 * Query operations are executed directly against the store.</p>
 */
public final class MemoryActor extends Actor<MemoryCommand> {

  private final @NotNull MemoryStore store;
  private final @NotNull MemorizationStrategy memorizationStrategy;

  /**
   * Creates a new {@link MemoryActor} with the given store and memorization strategy.
   *
   * @param store                the memory store used for query operations; must not be
   *                             {@code null}.
   * @param memorizationStrategy the strategy used to persist memories; must not be {@code null}.
   */
  public MemoryActor(
      final @NotNull MemoryStore store,
      final @NotNull MemorizationStrategy memorizationStrategy) {
    this.store = Objects.requireNonNull(store, "Memory store must not be null");
    this.memorizationStrategy = Objects.requireNonNull(memorizationStrategy,
        "Memorization strategy must not be null");
  }

  @Override
  public void onMessage(final @NotNull MemoryCommand message) {
    switch (message) {
      case MemoryCommand.Memorize cmd -> handleMemorize(cmd);
      case MemoryCommand.Query cmd -> handleQuery(cmd);
      case MemoryCommand.Forget cmd -> handleForget(cmd);
    }
  }

  private void handleMemorize(final @NotNull MemoryCommand.Memorize cmd) {
    try {
      final var id = memorizationStrategy.memorize(cmd.content(), cmd.metadata()).join();
      respondWith(new MemoryResponse.Written(id));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }

  private void handleQuery(final @NotNull MemoryCommand.Query cmd) {
    try {
      final var query = new MemoryQuery(cmd.embedding(), cmd.maxResults());
      final var entries = store.query(query).join();
      respondWith(new MemoryResponse.QueryResult(entries));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }

  private void handleForget(final @NotNull MemoryCommand.Forget cmd) {
    try {
      store.delete(cmd.memoryId()).join();
      respondWith(new MemoryResponse.Forgotten(cmd.memoryId()));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }
}
