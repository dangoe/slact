package de.dangoe.concurrent.slact.core.exception;

import de.dangoe.concurrent.slact.core.ActorPath;
import org.jetbrains.annotations.NotNull;

public class ActorRegistrationException extends SlactException {

  public ActorRegistrationException(final @NotNull ActorPath path) {
    super("An actor is already registered for path '%s'.".formatted(path));
  }
}
