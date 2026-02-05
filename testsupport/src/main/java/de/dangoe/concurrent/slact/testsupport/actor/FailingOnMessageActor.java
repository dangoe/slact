package de.dangoe.concurrent.slact.testsupport.actor;

import de.dangoe.concurrent.slact.core.Actor;
import org.jetbrains.annotations.NotNull;

public class FailingOnMessageActor<M> extends Actor<M> {

  @Override
  public void onMessage(@NotNull M message) {
    throw new AssertionError("Expected to receive no message, but received %s".formatted(message));
  }
}
