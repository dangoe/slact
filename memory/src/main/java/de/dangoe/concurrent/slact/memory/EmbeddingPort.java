package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Port for generating embedding vectors from text.
 */
public interface EmbeddingPort {

  /**
   * Computes an embedding vector for the given text.
   *
   * @param text the input text.
   * @return a future completing with the embedding vector.
   */
  @NotNull RichFuture<float[]> embed(@NotNull String text);
}
