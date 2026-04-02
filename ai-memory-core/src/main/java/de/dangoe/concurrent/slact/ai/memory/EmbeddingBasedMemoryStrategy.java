package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link MemoryStrategy} that uses an {@link EmbeddingPort} to embed content before
 * writing or querying, and delegates persistence to a {@link MemoryStore}.
 *
 * <p>All embedding and storage details are fully encapsulated — callers only provide raw text.</p>
 */
public final class EmbeddingBasedMemoryStrategy implements MemoryStrategy {

  private final @NotNull EmbeddingPort embeddingPort;
  private final @NotNull MemoryStore store;

  /**
   * Creates a new strategy.
   *
   * @param embeddingPort the port used to compute embeddings; must not be {@code null}.
   * @param store         the memory store used for persistence and queries; must not be
   *                      {@code null}.
   */
  public EmbeddingBasedMemoryStrategy(
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
        .thenCompose(computedEmbedding -> store.save(Memory.of(content, computedEmbedding, metadata)));
  }

  @Override
  public @NotNull RichFuture<List<MemoryEntry>> retrieve(
      final @NotNull String topic,
      final int maxResults) {
    return embeddingPort.embed(topic)
        .thenCompose(computedEmbedding -> store.query(new MemoryQuery(computedEmbedding, maxResults)));
  }

  @Override
  public @NotNull RichFuture<Void> delete(final @NotNull UUID id) {
    return store.delete(id);
  }
}
