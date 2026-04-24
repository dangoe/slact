// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.exception;

import de.dangoe.concurrent.slact.core.ActorHandle;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown, if a message is received that cannot be handled by the receiver actor.
 */
public class MessageRejectedException extends SlactException {

  /**
   * Constructs a {@link MessageRejectedException} for the given actor and message.
   *
   * @param actor   the actor that was unable to handle the message.
   * @param message the message that was rejected.
   */
  public MessageRejectedException(final @NotNull ActorHandle<?> actor,
      final @NotNull Object message) {
    super("Actor '%s' was not able to handle '%s'.".formatted(actor.path(), message));
  }
}
