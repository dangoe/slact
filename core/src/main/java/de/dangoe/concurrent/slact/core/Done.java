package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

/**
 * Marker class representing completion of an operation.
 */
public final class Done {

  @SuppressWarnings("InstantiationOfUtilityClass")
  private static final @NotNull Done instance = new Done();

  private Done() {
    // prevent instantiation
  }

  /**
   * Returns the singleton instance of Done.
   *
   * @return the singleton {@link Done} instance.
   */
  public static @NotNull Done instance() {
    return instance;
  }
}
