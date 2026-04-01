package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Strategy for persisting a candidate memory (given as raw text) to a backing store.
 *
 * <p>Each implementation encapsulates all storage-specific details — for example the
 * {@link EmbeddingBasedMemorizationStrategy} embeds the content internally before saving. Callers
 * never need to be aware of how the content is transformed or de-duplicated.
 */
public interface MemorizationStrategy {

  /**
   * Memorizes the given content and returns the assigned ID.
   *
   * @param content  the raw text to memorize.
   * @param metadata optional key-value metadata to attach.
   * @return a future completing with the stored memory's ID.
   */
  @NotNull RichFuture<UUID> memorize(@NotNull String content,
      @NotNull Map<String, String> metadata);
}

