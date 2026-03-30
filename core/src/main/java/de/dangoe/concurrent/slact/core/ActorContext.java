package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

/**
 * Provides contextual information and operations for the currently executing actor.
 * <p>
 * Extends {@link ActorRuntime} to allow message sending, forwarding, and response handling.
 * </p>
 *
 * @param <M> The type of messages the actor can receive.
 */
public interface ActorContext<M> extends ActorRuntime {

  /**
   * Returns the handle of the parent actor.
   *
   * @return the parent actor handle.
   */
  @NotNull ActorHandle<?> parent();

  /**
   * Returns the handle of the current actor.
   *
   * @return the current actor handle.
   */
  @NotNull ActorHandle<M> self();

  /**
   * Returns the handle of the sender of the current message.
   *
   * @return the sender actor handle.
   */
  @NotNull ActorHandle<?> sender();

  /**
   * Sends a reply to the sender of the current message.
   *
   * @param message the message to reply with.
   * @param <M1>    the type of the reply message.
   */
  <M1> void respondWith(@NotNull M1 message);
}
