package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an item in an actor's mailbox.
 */
public abstract class MailboxItem {

  /**
   * Message wrapper for mailbox items.
   *
   * @param <M> The message type.
   */
  public static abstract class WrappedMessage<M> extends MailboxItem {

    private final M message;

    /**
     * Constructs a wrapped message.
     *
     * @param message The message.
     * @param sender  The sender's actor path.
     */
    protected WrappedMessage(final @NotNull M message, final @NotNull ActorPath sender) {
      super(sender);
      this.message = message;
    }

    /**
     * Returns the wrapped message.
     *
     * @return The message.
     */
    public final @NotNull M message() {
      return this.message;
    }
  }

  /**
   * Fire-and-forget message for mailbox items.
   *
   * @param <M> The message type.
   */
  public static final class FireAndForgetMessage<M> extends WrappedMessage<M> {

    /**
     * Constructs a fire-and-forget message.
     *
     * @param message The message.
     * @param sender  The sender's actor path.
     */
    public FireAndForgetMessage(final @NotNull M message, final @NotNull ActorPath sender) {
      super(message, sender);
    }
  }

  /**
   * Message with response request for mailbox items.
   *
   * @param <M> The message type.
   * @param <R> The response type.
   */
  public static final class MessageWithResponseRequest<M, R> extends WrappedMessage<M> {

    private final CompletableFuture<R> future;

    /**
     * Constructs a message with response request.
     *
     * @param message The message.
     * @param sender  The sender's actor path.
     */
    public MessageWithResponseRequest(final @NotNull M message, final @NotNull ActorPath sender) {
      super(message, sender);
      this.future = new CompletableFuture<>();
    }

    /**
     * Returns the future for the response.
     *
     * @return The response future.
     */
    public @NotNull Future<R> future() {
      return future;
    }

    /**
     * Returns the internal future for the response.
     *
     * @return The internal response future.
     */
    @NotNull CompletableFuture<R> futureInternal() {
      return future;
    }
  }

  /**
   * Lifecycle control message for mailbox items.
   */
  public static abstract class LifecycleControlMessage extends MailboxItem {

    /**
     * Constructs a lifecycle control message.
     *
     * @param sender The sender's actor path.
     */
    public LifecycleControlMessage(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  /**
   * Complete start actor command for mailbox items.
   */
  public static final class CompleteStartActorCommand extends LifecycleControlMessage {

    /**
     * Constructs a complete start actor command.
     *
     * @param sender The sender's actor path.
     */
    public CompleteStartActorCommand(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  /**
   * Command to stop an actor.
   */
  public static final class StopActorCommand extends LifecycleControlMessage {

    /**
     * Constructs a stop actor command.
     *
     * @param sender The sender's actor path.
     */
    public StopActorCommand(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  /**
   * Command to try completing the stop of an actor.
   */
  public static final class TryCompleteStopActorCommand extends LifecycleControlMessage {

    /**
     * Constructs a try-complete stop actor command.
     *
     * @param sender The sender's actor path.
     */
    public TryCompleteStopActorCommand(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  /**
   * Event indicating an actor has stopped.
   */
  public static final class ActorStoppedEvent extends LifecycleControlMessage {

    /**
     * Constructs an actor stopped event.
     *
     * @param sender The sender's actor path.
     */
    public ActorStoppedEvent(final @NotNull ActorPath sender) {
      super(sender);
    }
  }

  private static final AtomicLong idSequence = new AtomicLong(0);

  private final long id;

  private final ActorPath sender;

  protected MailboxItem(final @NotNull ActorPath sender) {
    super();
    this.id = idSequence.getAndIncrement();
    this.sender = sender;
  }

  /**
   * Returns the unique identifier for this mailbox item.
   *
   * @return The item id.
   */
  public final long id() {
    return this.id;
  }

  /**
   * Returns the sender's actor path for this mailbox item.
   *
   * @return The sender's actor path.
   */
  public final @NotNull ActorPath sender() {
    return sender;
  }

  @Override
  public String toString() {
    return "%s{id=%s, sender=%s}".formatted(getClass().getSimpleName(), id, sender);
  }
}
