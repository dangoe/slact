package de.dangoe.concurrent.slact.testkit.model;

import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ReceivedMessage<M>(@NotNull M message, @NotNull ActorPath sender) {

  public ReceivedMessage {
    Objects.requireNonNull(message, "Message must not be null");
    Objects.requireNonNull(sender, "Sender must not be null");
  }

  public ReceivedMessage(@NotNull M message, @NotNull ActorHandle<?> sender) {
    this(message, sender.path());
  }
}
