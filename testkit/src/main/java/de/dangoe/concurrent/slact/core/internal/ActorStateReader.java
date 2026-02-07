package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.ActorRuntime;
import org.jetbrains.annotations.NotNull;

public final class ActorStateReader {

  private final @NotNull ActorRuntime actorRuntime;

  public ActorStateReader(final @NotNull ActorRuntime actorRuntime) {
    this.actorRuntime = actorRuntime;
  }

  public @NotNull ActorState readState(final @NotNull ActorPath path) {
    return ((ActorWrapper<?>) actorRuntime.resolve(path).orElseThrow(() -> new AssertionError(
        "Failed to resolve actor with path '%s'.".formatted(path)))).state();
  }
}
