package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.ActorRuntime;
import org.jetbrains.annotations.NotNull;

/**
 * Utility for checking actor readiness and startup completion in tests.
 */
public final class ActorReadinessResolver {

  private final @NotNull ActorRuntime actorRuntime;

  /**
   * Creates a new resolver for the given actor runtime.
   *
   * @param actorRuntime The actor runtime.
   */
  public ActorReadinessResolver(final @NotNull ActorRuntime actorRuntime) {
    this.actorRuntime = actorRuntime;
  }

  /**
   * Checks if the actor at the given path is ready.
   *
   * @param path The actor path.
   * @return True if ready, false otherwise.
   */
  public boolean isReady(final @NotNull ActorPath path) {
    return ((ActorWrapper<?>) actorRuntime.resolve(path).orElseThrow(() -> new AssertionError(
        "Failed to resolve actor with path '%s'.".formatted(path)))).isReady();
  }

  /**
   * Checks if the actor at the given path has completed startup.
   *
   * @param path The actor path.
   * @return True if startup is complete, false otherwise.
   */
  public boolean isStartupComplete(final @NotNull ActorPath path) {
    return ((ActorWrapper<?>) actorRuntime.resolve(path).orElseThrow(() -> new AssertionError(
        "Failed to resolve actor with path '%s'.".formatted(path)))).isStartupComplete();
  }
}
