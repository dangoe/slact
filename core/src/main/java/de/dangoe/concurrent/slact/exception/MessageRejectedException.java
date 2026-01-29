package de.dangoe.concurrent.slact.exception;

import de.dangoe.concurrent.slact.ActorHandle;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown, if a message is received that cannot be handled by the receiver actor.
 */
public class MessageRejectedException extends SlactException {

  public MessageRejectedException(final @NotNull ActorHandle<?> actor,
      final @NotNull Object message) {
    super("Actor '%s' was not able to handle '%s'.".formatted(actor.path(), message));
  }
}
