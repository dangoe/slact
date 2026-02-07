package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorHandle;
import org.jetbrains.annotations.NotNull;

public final class ActorStateReader {

  private ActorStateReader() {
    // prevent instantiation
  }

  public static @NotNull ActorState readState(final @NotNull ActorHandle<?> handle) {
    return ((ActorWrapper<?>) handle).state();
  }
}
