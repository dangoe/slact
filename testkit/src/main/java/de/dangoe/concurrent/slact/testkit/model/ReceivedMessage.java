package de.dangoe.concurrent.slact.testkit.model;

import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a message received by an actor in tests.
 *
 * @param <M>     the message type.
 * @param message the received message.
 * @param sender  the sender's actor path.
 */
public record ReceivedMessage<M>(@NotNull M message, @NotNull ActorPath sender) {

  /**
   * Constructs a ReceivedMessage with an ActorHandle sender.
   *
   * @param message the message.
   * @param sender  the sender handle.
   */
  public ReceivedMessage(@NotNull M message, @NotNull ActorHandle<?> sender) {
    this(message, sender.path());
  }

  /**
   * Validates the message and sender fields.
   */
  public ReceivedMessage {
    Objects.requireNonNull(message, "Message must not be null");
    Objects.requireNonNull(sender, "Sender must not be null");
  }
}
