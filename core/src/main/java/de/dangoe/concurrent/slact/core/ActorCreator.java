package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating actor instances.
 *
 * @param <A> The actor type.
 * @param <M> The message type.
 */
@FunctionalInterface
public interface ActorCreator<A extends Actor<M>, M> {

  /**
   * Creates a new actor instance.
   *
   * @return The created actor.
   */
  @NotNull A create();
}
