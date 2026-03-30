package de.dangoe.concurrent.slact.core.exception;

import de.dangoe.concurrent.slact.core.ActorPath;
import org.jetbrains.annotations.NotNull;

/**
 * Exception thrown when an actor registration fails due to a duplicate path.
 */
public class ActorRegistrationException extends SlactException {

  /**
   * Constructs an ActorRegistrationException for the given path.
   *
   * @param path the actor path.
   */
  public ActorRegistrationException(final @NotNull ActorPath path) {
    super("An actor is already registered for path '%s'.".formatted(path));
  }
}
