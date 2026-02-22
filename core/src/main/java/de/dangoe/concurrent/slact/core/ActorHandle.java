package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

/**
 * Handle for referencing and sending messages to actors.
 *
 * @param <M> The type of messages the actor can receive.
 */
public interface ActorHandle<M> {

  /**
   * Returns the path of the actor.
   *
   * @return The actor's path.
   */
  @NotNull ActorPath path();

  /**
   * Sends a message to the actor.
   *
   * @param message The message to send.
   */
  void send(@NotNull M message);
}
