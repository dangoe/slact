package de.dangoe.concurrent.slact.core.testhelper;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import org.jetbrains.annotations.NotNull;

public class ResendActor<M> extends Actor<M> {

  private final ActorHandle<M> receiver;

  public ResendActor(final @NotNull ActorHandle<M> receiver) {
    this.receiver = receiver;
  }

  @Override
  public void onMessage(@NotNull M message) {
    send(message).to(receiver);
  }
}
