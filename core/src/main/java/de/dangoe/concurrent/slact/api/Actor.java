package de.dangoe.concurrent.slact.api;

import de.dangoe.concurrent.slact.api.ActorContext.PreparedForwardMessageOp;
import de.dangoe.concurrent.slact.api.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.api.exception.IncompatibleMessageReceiverException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class Actor<M> {

  private ActorContext context;

  private ActorHandle<?> sender;

  public final void onMessage(final M message, final ActorContext context) {
    this.context = context;
    onMessageInternal(message);
  }

  protected abstract void onMessageInternal(M message);

  protected final ActorContext context() {
    return this.context;
  }

  protected final ActorHandle<?> parent() {
    return this.context.parent();
  }

  protected final ActorHandle<?> self() {
    return this.context.self();
  }

  protected final ActorHandle<?> sender() {
    return this.context.sender();
  }

  @SuppressWarnings("unchecked")
  protected final <M1> void respond(final M1 message) {
    try {
      context().send(message).to((ActorHandle<? extends M1>) sender());
    } catch (final ClassCastException e) {
      throw new IncompatibleMessageReceiverException(e);
    }
  }

  protected final <M1> PreparedSendMessageOp<M1> send(final M1 message) {
    return context().send(message);
  }

  protected final <M1> PreparedForwardMessageOp<M1> forward(final M1 message) {
    return context().forward(message);
  }

  protected final PipeOp<M> pipe(final Future<M> eventualMessage){
      return context().pipe(eventualMessage);
  }
}
