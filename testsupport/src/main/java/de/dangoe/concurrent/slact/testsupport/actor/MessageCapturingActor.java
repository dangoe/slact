package de.dangoe.concurrent.slact.testsupport.actor;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

public class MessageCapturingActor<M> extends Actor<M> {

  public record MessageWithSender<M>(@NotNull M message, @NotNull ActorHandle<M> sender) {

  }

  private final @NotNull List<MessageWithSender<M>> messages = new CopyOnWriteArrayList<>();

  @Override
  public void onMessage(@NotNull M message) {
    messages.add(new MessageWithSender<>(message, sender()));
  }

  public List<MessageWithSender<M>> receivedMessages() {
    return this.messages;
  }
}
