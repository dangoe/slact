package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.Actor;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Actor that handles both memory write ({@link MemoryCommand.Memorize}) and read
 * ({@link MemoryCommand.Query}) commands against a {@link MemoryStore}.
 *
 * <p>Write operations are delegated to a {@link MemorizationStrategy}, defaulting to
 * {@link EmbeddingBasedMemorizationStrategy}. Query operations are executed directly against the
 * store.</p>
 */
public final class MemoryActor extends Actor<MemoryCommand> {

  private final @NotNull MemoryStore store;
  private final @NotNull MemorizationStrategy memorizationStrategy;

  /**
   * Creates a new {@link MemoryActor} with the default {@link EmbeddingBasedMemorizationStrategy}.
   *
   * @param store the memory store; must not be {@code null}.
   */
  public MemoryActor(final @NotNull MemoryStore store) {
    this(store, new EmbeddingBasedMemorizationStrategy(store));
  }

  /**
   * Creates a new {@link MemoryActor} with a custom {@link MemorizationStrategy}.
   *
   * @param store                 the memory store; must not be {@code null}.
   * @param memorizationStrategy  the strategy used to write memories; must not be {@code null}.
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
    }
  }

  private void handleMemorize(final @NotNull MemoryCommand.Memorize cmd) {
    try {
      final var memory = Memory.of(cmd.content(), cmd.embedding(), cmd.metadata());
      final var id = memorizationStrategy.memorize(memory).join();
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
}
