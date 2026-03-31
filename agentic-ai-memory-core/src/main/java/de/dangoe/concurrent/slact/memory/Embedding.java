package de.dangoe.concurrent.slact.memory;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * Value object wrapping a pre-computed embedding vector.
 *
 * @param values the embedding vector values.
 */
public record Embedding(float @NotNull [] values) {

  public Embedding {
    values = values.clone();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Embedding(float[] values1))) {
      return false;
    }
    return Arrays.equals(values, values1);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }

  @Override
  public @NotNull String toString() {
    return "Embedding" + Arrays.toString(values);
  }
}
