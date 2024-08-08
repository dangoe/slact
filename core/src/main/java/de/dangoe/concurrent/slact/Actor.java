package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorContext.PreparedForwardMessageOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageWithResponseRequestOp;
import de.dangoe.concurrent.slact.exception.MessageRejectedException;
import java.util.concurrent.Future;

public abstract class Actor<M> implements MessageReceiver<M> {

  protected final MessageReceiver<M> defaultBehaviour = new MessageReceiver<M>() {
    @Override
    public void onMessage(M message) {
      Actor.this.onMessage(message);
    }
  };

  private MessageReceiver<M> behaviour = defaultBehaviour;

  private ActorContext context;

  @SuppressWarnings("unchecked")
  final void onMessage(final Object message, final ActorContext context) {
    this.context = context;

    try {
      behaviour.onMessage((M) message);
    } catch (final ClassCastException e) {
      throw new MessageRejectedException(self(), message);
    }
  }

  protected final void behaveAs(final MessageReceiver<M> behaviour) {
    this.behaviour = behaviour;
  }

  protected final void reject(final M message) {
    throw new MessageRejectedException(self(), message);
  }

  protected final ActorContext context() {
    return this.context;
  }

  @SuppressWarnings("unchecked")
  protected final <M1> ActorHandle<M1> parent() {
    return (ActorHandle<M1>) this.context.parent();
  }

  @SuppressWarnings("unchecked")
  protected final ActorHandle<M> self() {
    return (ActorHandle<M>) this.context.self();
  }

  @SuppressWarnings("unchecked")
  protected final <M1> ActorHandle<M1> sender() {
    return (ActorHandle<M1>) this.context.sender();
  }

  /**
   * Sends a reply to the sender of the received message.
   *
   * @param message The message to be replied with.
   */
  protected final void respondWith(final Object message) {
    context().respondWith(message);
  }

  protected final <M1> PreparedSendMessageOp<M1> send(final M1 message) {
    return context().send(message);
  }

  protected final <M1> PreparedForwardMessageOp<M1> forward(final M1 message) {
    return context().forward(message);
  }

  protected final FuturePipeOp<M> pipe(final Future<M> eventualMessage) {
    return context().pipeFuture(eventualMessage);
  }

  @SuppressWarnings("unchecked")
  public <M1, R> PreparedSendMessageWithResponseRequestOp<M1, R> requestResponseTo(
      final M1 message) {
    return targetActor -> ((ActorWrapper<M1>) targetActor).requestResponseToInternal(message,
        self());
  }
}
