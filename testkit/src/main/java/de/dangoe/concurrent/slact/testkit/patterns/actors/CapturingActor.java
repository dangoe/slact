package de.dangoe.concurrent.slact.testkit.patterns.actors;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

public class CapturingActor<M> extends Actor<M> {

  private final @NotNull List<ReceivedMessage<M>> messages = new CopyOnWriteArrayList<>();

  @Override
  public void onMessage(@NotNull M message) {
    messages.add(new ReceivedMessage<>(message, sender().path()));
  }

  public @NotNull List<ReceivedMessage<M>> receivedMessages() {
    return this.messages;
  }
}
