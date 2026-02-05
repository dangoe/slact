package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

public final class Done {

  @SuppressWarnings("InstantiationOfUtilityClass")
  private static final @NotNull Done instance = new Done();

  private Done() {
    // prevent instantiation
  }

  public static @NotNull Done instance() {
    return instance;
  }
}
