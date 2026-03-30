package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

/**
 * Receives messages for an actor or message handler.
 *
 * @param <M> the type of messages to receive.
 */
public interface MessageReceiver<M> {

  /**
   * Handles an incoming message.
   *
   * @param message the message to handle.
   */
  void onMessage(@NotNull M message);
}
