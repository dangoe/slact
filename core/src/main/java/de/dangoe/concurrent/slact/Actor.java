package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorRuntime.SendMessageOp;
import de.dangoe.concurrent.slact.exception.MessageRejectedException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public abstract class Actor<M> implements MessageReceiver<M> {

  protected final @NotNull MessageReceiver<M> defaultBehaviour = Actor.this;

  private @NotNull MessageReceiver<M> behaviour = defaultBehaviour;

  // Controlled by runtime. Not null during regular lifecycle phases
  @SuppressWarnings("NotNullFieldNotInitialized")
  private @NotNull ActorContext<?> context;

  final void onStart(final @NotNull ActorContext<?> context) {
    this.context = context;
    onStart();
  }

  void onStart() {
    // Empty default hook
  }

  final void onStop(final @NotNull ActorContext<?> context) {
    this.context = context;
    onStop();
  }

  void onStop() {
    // Empty default hook
  }

  @SuppressWarnings("unchecked")
  final void onMessage(final @NotNull Object message, final @NotNull ActorContext<?> context) {

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

  protected final void behaveAsDefault() {
    this.behaviour = defaultBehaviour;
  }

  protected final void reject(final @NotNull M message) {
    throw new MessageRejectedException(self(), message);
  }

  protected final @NotNull ActorContext<?> context() {
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
  protected final <M1> @NotNull ActorHandle<M1> sender() {
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
