// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

/**
 * Operation for piping a future message to a target actor.
 *
 * @param <M> the message type.
 */
@FunctionalInterface
public interface FuturePipeOp<M> {

  /**
   * Pipes the future message to the specified target actor.
   *
   * @param target the actor to receive the message.
   */
  void to(@NotNull ActorHandle<? extends M> target);
}
