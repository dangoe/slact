package de.dangoe.concurrent.slact.testkit.patterns.actors;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * Actor implementation for capturing received messages in tests.
 *
 * @param <M> The message type.
 */
public class CapturingActor<M> extends Actor<M> {

  private final @NotNull List<ReceivedMessage<M>> messages = new CopyOnWriteArrayList<>();

  public CapturingActor() {
    super();
  }

  @Override
  public void onMessage(@NotNull M message) {
    messages.add(new ReceivedMessage<>(message, sender().path()));
  }

  /**
   * Returns the list of received messages.
   *
   * @return List of received messages.
   */
  public @NotNull List<ReceivedMessage<M>> receivedMessages() {
    return this.messages;
  }
}
