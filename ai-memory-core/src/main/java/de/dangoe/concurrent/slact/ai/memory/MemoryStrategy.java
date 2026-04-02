package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Unified strategy for all memory operations: writing, reading, and deletion.
 *
 * <p>Each implementation encapsulates all storage- and embedding-specific details. Callers
 * never need to be aware of how content is transformed, embedded, or de-duplicated.</p>
 *
 * <p>The {@link EmbeddingBasedMemoryStrategy} is the default implementation: it uses an
 * {@link EmbeddingPort} internally to compute embeddings before saving or querying.</p>
 */
public interface MemoryStrategy {

  /**
   * Memorizes the given content and returns the assigned ID.
   *
   * @param content  the raw text to memorize.
   * @param metadata optional key-value metadata to attach.
   * @return a future completing with the stored memory's ID.
   */
  @NotNull RichFuture<UUID> memorize(@NotNull String content,
      @NotNull Map<String, String> metadata);

  /**
   * Retrieves memories similar to the given criteria.
   *
   * @param criteria   the raw text criteria to match against.
   * @param maxResults the maximum number of results to return.
   * @return a future completing with the matching memory entries.
   */
  @NotNull RichFuture<List<MemoryEntry>> retrieve(@NotNull String criteria, int maxResults);

  /**
   * Deletes a memory entry by its ID. Completes normally if the ID does not exist.
   *
   * @param id the ID of the memory entry to delete.
   * @return a future completing when the deletion is done.
   */
  @NotNull RichFuture<Void> delete(@NotNull UUID id);
}
