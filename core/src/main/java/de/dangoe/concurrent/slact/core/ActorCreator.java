// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating actor instances.
 *
 * @param <A> the actor type.
 * @param <M> the message type.
 */
@FunctionalInterface
public interface ActorCreator<A extends Actor<M>, M> {

  /**
   * Creates a new actor instance.
   *
   * @return the created actor.
   */
  @NotNull A create();
}
