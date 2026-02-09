package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.ActorRuntime;
import org.jetbrains.annotations.NotNull;

public final class ActorReadinessResolver {

  private final @NotNull ActorRuntime actorRuntime;

  public ActorReadinessResolver(final @NotNull ActorRuntime actorRuntime) {
    this.actorRuntime = actorRuntime;
  }

  public boolean isReady(final @NotNull ActorPath path) {
    return ((ActorWrapper<?>) actorRuntime.resolve(path).orElseThrow(() -> new AssertionError(
        "Failed to resolve actor with path '%s'.".formatted(path)))).isReady();
  }
}
