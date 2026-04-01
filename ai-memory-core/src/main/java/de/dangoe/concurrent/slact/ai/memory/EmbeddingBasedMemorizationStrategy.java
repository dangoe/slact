package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link MemorizationStrategy} that delegates directly to the underlying {@link MemoryStore}.
 */
public final class EmbeddingBasedMemorizationStrategy implements MemorizationStrategy {

  private final @NotNull MemoryStore store;

  /**
   * Creates a new strategy backed by the given store.
   *
   * @param store the memory store to write to; must not be {@code null}.
   */
  public EmbeddingBasedMemorizationStrategy(final @NotNull MemoryStore store) {
    this.store = Objects.requireNonNull(store, "Memory store must not be null");
  }

  @Override
  public @NotNull RichFuture<UUID> memorize(final @NotNull Memory memory) {
    return store.save(memory);
  }
}
