package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public abstract class MailboxItem {

  public static abstract class WrappedMessage<M> extends MailboxItem {

    private final M message;

    protected WrappedMessage(final @NotNull M message, final @NotNull ActorPath sender) {
      super(sender);
      this.message = message;
    }

    public final @NotNull M message() {
      return this.message;
    }
  }

  public static final class FireAndForgetMessage<M> extends WrappedMessage<M> {

    public FireAndForgetMessage(final @NotNull M message, final @NotNull ActorPath sender) {
      super(message, sender);
    }
  }

  public static final class MessageWithResponseRequest<M, R> extends WrappedMessage<M> {

    private final CompletableFuture<R> future;

    public MessageWithResponseRequest(final @NotNull M message, final @NotNull ActorPath sender) {
      super(message, sender);
      this.future = new CompletableFuture<>();
    }

    public @NotNull Future<R> future() {
      return future;
    }

    @NotNull CompletableFuture<R> futureInternal() {
      return future;
    }
  }

  public static abstract class LifecycleControlMessage extends MailboxItem {

    public LifecycleControlMessage(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  public static final class StartActorCommand extends LifecycleControlMessage {

    public StartActorCommand(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  public static final class ActorStartedEvent extends LifecycleControlMessage {

    public ActorStartedEvent(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  public static final class StopActorCommand extends LifecycleControlMessage {

    public StopActorCommand(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  public static final class TryCompleteStopActorCommand extends LifecycleControlMessage {

    public TryCompleteStopActorCommand(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  public static final class ActorStoppedEvent extends LifecycleControlMessage {

    public ActorStoppedEvent(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  private final String id;

  private final ActorPath sender;

  protected MailboxItem(final @NotNull ActorPath sender) {
    super();
    this.id = UUID.randomUUID().toString();
    this.sender = sender;
  }

  public final @NotNull String id() {
    return this.id;
  }

  public final @NotNull ActorPath sender() {
    return sender;
  }

  @Override
  public String toString() {
    return "%s{id=%s, sender=%s}".formatted(getClass().getSimpleName(), id, sender);
  }
}
