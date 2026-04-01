package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link MemorizationStrategy} that embeds the content using an {@link EmbeddingPort} and
 * persists the resulting {@link Memory} to a {@link MemoryStore}.
 *
 * <p>All embedding and storage details are fully encapsulated — callers only provide raw text.
 */
public final class EmbeddingBasedMemorizationStrategy implements MemorizationStrategy {

  private final @NotNull EmbeddingPort embeddingPort;
  private final @NotNull MemoryStore store;

  /**
   * Creates a new strategy.
   *
   * @param embeddingPort the port used to compute embeddings; must not be {@code null}.
   * @param store         the memory store to write to; must not be {@code null}.
   */
  public EmbeddingBasedMemorizationStrategy(
      final @NotNull EmbeddingPort embeddingPort,
      final @NotNull MemoryStore store) {
    this.embeddingPort = Objects.requireNonNull(embeddingPort, "EmbeddingPort must not be null");
    this.store = Objects.requireNonNull(store, "Memory store must not be null");
  }

  @Override
  public @NotNull RichFuture<UUID> memorize(
      final @NotNull String content,
      final @NotNull Map<String, String> metadata) {
    return embeddingPort.embed(content)
        .thenCompose(embedding -> store.save(Memory.of(content, embedding, metadata)));
  }
}
