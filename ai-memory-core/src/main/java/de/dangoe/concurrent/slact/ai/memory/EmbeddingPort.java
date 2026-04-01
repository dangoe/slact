package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Port for generating embedding vectors from text.
 */
public interface EmbeddingPort {

  /**
   * Computes an embedding for the given text.
   *
   * @param text the input text.
   * @return a future completing with the embedding.
   */
  @NotNull RichFuture<Embedding> embed(@NotNull String text);
}
