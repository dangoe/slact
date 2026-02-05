package de.dangoe.concurrent.slact.testkit.patterns.actors;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import org.jetbrains.annotations.NotNull;

public class ForwardingActor<M> extends Actor<M> {

  private final ActorHandle<M> receiver;

  public ForwardingActor(final @NotNull ActorHandle<M> receiver) {
    this.receiver = receiver;
  }

  @Override
  public void onMessage(@NotNull M message) {
    forward(message).to(receiver);
  }
}
