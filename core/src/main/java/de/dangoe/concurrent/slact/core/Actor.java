package de.dangoe.concurrent.slact.core;

import de.dangoe.concurrent.slact.core.ActorRuntime.SendMessageOp;
import de.dangoe.concurrent.slact.core.exception.MessageRejectedException;
import de.dangoe.concurrent.slact.core.internal.ActiveActorContextHolder;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for actors.
 * <p>
 * Actors process messages, manage their own state, and interact with other actors. Subclasses
 * should implement {@link MessageReceiver#onMessage(Object)} to define message handling.
 * </p>
 *
 * @param <M> The type of messages this actor can receive.
 */
public abstract class Actor<M> implements MessageReceiver<M> {

  private final @NotNull ActiveActorContextHolder activeActorContextHolder = ActiveActorContextHolder.getInstance();

  protected final @NotNull MessageReceiver<M> defaultBehaviour = Actor.this;

  private @NotNull MessageReceiver<M> behaviour = defaultBehaviour;

  /**
   * Called when the actor is started. Override to implement custom startup logic.
   */
  public void onStart() {
    // Empty default hook
  }

  /**
   * Called when the actor is stopped. Override to implement custom shutdown logic.
   */
  public void onStop() {
    // Empty default hook
  }

  /**
   * Receives a message and delegates to the current behaviour. Throws
   * {@link MessageRejectedException} if the message type is invalid.
   *
   * @param message The message to process.
   */
  public final void receiveMessage(final @NotNull M message) {
    try {
      behaviour.onMessage(message);
    } catch (final ClassCastException e) {
      throw new MessageRejectedException(self(), message);
    }
  }

  /**
   * Returns the current actor context.
   *
   * @return The actor context for this actor instance.
   */
  @SuppressWarnings("unchecked")
  protected final ActorContext<M> context() {
    return this.activeActorContextHolder.activeContext().map(it -> (ActorContext<M>) it)
        .orElseThrow(() -> new IllegalStateException(
            "No active context found."
        ));
  }

  /**
   * Changes the actor's behaviour to the given message receiver.
   *
   * @param behaviour The new behaviour.
   */
  protected final void behaveAs(final @NotNull MessageReceiver<M> behaviour) {
    this.behaviour = behaviour;
  }

  /**
   * Resets the actor's behaviour to the default.
   */
  protected final void behaveAsDefault() {
    this.behaviour = defaultBehaviour;
  }

  /**
   * Rejects the given message, throwing a {@link MessageRejectedException}.
   *
   * @param message The message to reject.
   */
  protected final void reject(final @NotNull M message) {
    throw new MessageRejectedException(self(), message);
  }

  /**
   * Returns the parent actor handle.
   *
   * @return The parent actor handle.
   */
  @SuppressWarnings("unchecked")
  protected final @NotNull <M1> ActorHandle<M1> parent() {
    return (ActorHandle<M1>) context().parent();
  }

  /**
   * Returns the handle for this actor.
   *
   * @return The actor's own handle.
   */
  protected final @NotNull ActorHandle<M> self() {
    return context().self();
  }

  /**
   * Returns the sender actor handle.
   *
   * @return The sender actor handle.
   */
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

  /**
   * Sends a message to another actor.
   *
   * @param message The message to send.
   * @param <M1>    The type of the message.
   * @return An operation to specify the target actor.
   */
  protected final <M1> @NotNull SendMessageOp<M1> send(final @NotNull M1 message) {
    return context().send(message);
  }

  /**
   * Forwards a message to another actor.
   *
   * @param message The message to forward.
   * @param <M1>    The type of the message.
   * @return An operation to specify the target actor.
   */
  protected final <M1> @NotNull SendMessageOp<M1> forward(final @NotNull M1 message) {
    return context().forward(message);
  }

  /**
   * Pipes a future message to another actor.
   *
   * @param eventualMessage The future message.
   * @return An operation to specify the target actor.
   */
  protected final @NotNull FuturePipeOp<M> pipeFuture(final @NotNull Future<M> eventualMessage) {
    return context().pipeFuture(eventualMessage);
  }
}
