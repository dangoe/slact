package de.dangoe.concurrent.slact.exception;

import de.dangoe.concurrent.slact.ActorPath;

public class ActorRegistrationException extends SlactException {

  public ActorRegistrationException(final ActorPath path) {
    super("An actor is already registered for path '%s'.".formatted(path));
  }
}
