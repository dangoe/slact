// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorContext;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the currently active actor context for each thread.
 * <p>
 * Used internally to manage actor context activation and deactivation.
 * </p>
 */
public final class ActiveActorContextHolder {

  private static final @NotNull ActiveActorContextHolder instance = new ActiveActorContextHolder();

  private final @NotNull ThreadLocal<ActorContext<?>> activeContextHolder = new ThreadLocal<>();

  private ActiveActorContextHolder() {
    // prevent initialization
  }

  /**
   * Returns the singleton instance.
   *
   * @return the singleton instance.
   */
  public static @NotNull ActiveActorContextHolder getInstance() {
    return instance;
  }

  /**
   * Returns the currently active actor context, if any.
   *
   * @return the active actor context, or empty if none.
   */
  public @NotNull Optional<ActorContext<?>> activeContext() {
    return Optional.ofNullable(this.activeContextHolder.get());
  }

  /**
   * Activates the given actor context for the current thread.
   *
   * @param context the actor context to activate.
   */
  public void activateContext(final @NotNull ActorContext<?> context) {
    this.activeContextHolder.set(context);
  }

  /**
   * Deactivates the actor context for the current thread.
   */
  public void deactivateContext() {
    this.activeContextHolder.remove();
  }
}
