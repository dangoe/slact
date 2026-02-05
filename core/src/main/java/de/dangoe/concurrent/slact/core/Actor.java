package de.dangoe.concurrent.slact.core;

import de.dangoe.concurrent.slact.core.ActorRuntime.SendMessageOp;
import de.dangoe.concurrent.slact.core.exception.MessageRejectedException;
import de.dangoe.concurrent.slact.core.internal.ActiveActorContextHolder;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public abstract class Actor<M> implements MessageReceiver<M> {

  private final @NotNull ActiveActorContextHolder activeActorContextHolder = ActiveActorContextHolder.getInstance();

  protected final @NotNull MessageReceiver<M> defaultBehaviour = Actor.this;

  private @NotNull MessageReceiver<M> behaviour = defaultBehaviour;

  public void onStart() {
    // Empty default hook
  }


  public void onStop() {
    // Empty default hook
  }

  public final void receiveMessage(final @NotNull M message) {
    try {
      behaviour.onMessage(message);
    } catch (final ClassCastException e) {
      throw new MessageRejectedException(self(), message);
    }
  }

  @SuppressWarnings("unchecked")
  protected final ActorContext<M> context() {
    return this.activeActorContextHolder.activeContext().map(it -> (ActorContext<M>) it)
        .orElseThrow(() -> new IllegalStateException(
            "No active context found."
        ));
  }

  protected final void behaveAs(final @NotNull MessageReceiver<M> behaviour) {
    this.behaviour = behaviour;
  }

  protected final void behaveAsDefault() {
    this.behaviour = defaultBehaviour;
  }

  protected final void reject(final @NotNull M message) {
    throw new MessageRejectedException(self(), message);
  }

  @SuppressWarnings("unchecked")
  protected final @NotNull <M1> ActorHandle<M1> parent() {
    return (ActorHandle<M1>) context().parent();
  }

  protected final @NotNull ActorHandle<M> self() {
    return context().self();
  }

  @SuppressWarnings("unchecked")
  protected final <M1> @NotNull ActorHandle<M1> sender() {
    return (ActorHandle<M1>) context().sender();
  }

  /**
   * Sends a reply to the sender of the received message.
   *
   * @param message The message to be replied with.
   */
  protected final void respondWith(final @NotNull Object message) {
    context().respondWith(message);
  }

  protected final <M1> @NotNull SendMessageOp<M1> send(final @NotNull M1 message) {
    return context().send(message);
  }

  protected final <M1> @NotNull SendMessageOp<M1> forward(final @NotNull M1 message) {
    return context().forward(message);
  }

  protected final @NotNull FuturePipeOp<M> pipeFuture(final @NotNull Future<M> eventualMessage) {
    return context().pipeFuture(eventualMessage);
  }
}
