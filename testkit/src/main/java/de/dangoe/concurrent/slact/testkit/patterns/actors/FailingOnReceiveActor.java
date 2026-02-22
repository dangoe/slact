package de.dangoe.concurrent.slact.testkit.patterns.actors;

import de.dangoe.concurrent.slact.core.Actor;
import org.jetbrains.annotations.NotNull;

/**
 * Actor implementation that fails on any received message (for negative tests).
 *
 * @param <M> The message type.
 */
public class FailingOnReceiveActor<M> extends Actor<M> {

  public FailingOnReceiveActor() {
    super();
  }

  @Override
  public void onMessage(@NotNull M message) {
    throw new AssertionError("Expected to receive no message, but received %s".formatted(message));
  }
}
