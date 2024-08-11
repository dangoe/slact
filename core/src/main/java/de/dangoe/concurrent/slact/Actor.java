package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorContext.PreparedForwardMessageOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageWithResponseRequestOp;
import de.dangoe.concurrent.slact.exception.MessageRejectedException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public abstract class Actor<M> implements MessageReceiver<M> {

  protected final MessageReceiver<M> defaultBehaviour = new MessageReceiver<M>() {
    @Override
    public void onMessage(@NotNull M message) {
      Actor.this.onMessage(message);
    }
  };

  private MessageReceiver<M> behaviour = defaultBehaviour;

  private ActorContext context;

  @SuppressWarnings("unchecked")
  final void onMessage(final @NotNull Object message, final @NotNull ActorContext context) {

    this.context = context;

    try {
      behaviour.onMessage((M) message);
    } catch (final ClassCastException e) {
      throw new MessageRejectedException(self(), message);
    }
  }

  protected final void behaveAs(final @NotNull MessageReceiver<M> behaviour) {
    this.behaviour = behaviour;
  }

  protected final void reject(final @NotNull M message) {
    throw new MessageRejectedException(self(), message);
  }

  protected final @NotNull ActorContext context() {
    return this.context;
  }

  @SuppressWarnings("unchecked")
  protected final @NotNull <M1> ActorHandle<M1> parent() {
    return (ActorHandle<M1>) this.context.parent();
  }

  @SuppressWarnings("unchecked")
  protected final @NotNull ActorHandle<M> self() {
    return (ActorHandle<M>) this.context.self();
  }

  @SuppressWarnings("unchecked")
  protected final @NotNull <M1> ActorHandle<M1> sender() {
    return (ActorHandle<M1>) this.context.sender();
  }

  /**
   * Sends a reply to the sender of the received message.
   *
   * @param message The message to be replied with.
   */
  protected final void respondWith(final @NotNull Object message) {
    context().respondWith(message);
  }

  protected final @NotNull <M1> PreparedSendMessageOp<M1> send(final @NotNull M1 message) {
    return context().send(message);
  }

  protected final @NotNull <M1> PreparedForwardMessageOp<M1> forward(final @NotNull M1 message) {
    return context().forward(message);
  }

  protected final @NotNull FuturePipeOp<M> pipeFuture(final @NotNull Future<M> eventualMessage) {
    return context().pipeFuture(eventualMessage);
  }

  @SuppressWarnings("unchecked")
  public @NotNull <M1, R> PreparedSendMessageWithResponseRequestOp<M1, R> requestResponseTo(
      final @NotNull M1 message) {
    return targetActor -> ((ActorWrapper<M1>) targetActor).requestResponseToInternal(message,
        self());
  }
}
