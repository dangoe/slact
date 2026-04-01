package de.dangoe.concurrent.slact.ai.memory;

/**
 * Value object representing a cosine similarity score in the range [0, 1].
 *
 * @param value the score value, must be between 0.0 and 1.0 inclusive.
 */
public record Score(double value) {

  public Score {
    if (value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(
          "Score must be between 0.0 and 1.0, but was: " + value);
    }
  }
}
