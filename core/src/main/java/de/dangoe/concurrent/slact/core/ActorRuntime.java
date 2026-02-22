package de.dangoe.concurrent.slact.core;

import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

/**
 * Provides runtime operations for actors, including message sending, forwarding, stopping, and
 * response requests.
 * <p>
 * Extends {@link ActorHandleResolver} and {@link ActorSpawner} for actor management.
 * </p>
 */
public interface ActorRuntime extends ActorHandleResolver, ActorSpawner {

  /**
   * Operation for sending a message to a target actor.
   *
   * @param <M> The message type.
   */
  @FunctionalInterface
  interface SendMessageOp<M> {

    /**
     * Sends the message to the specified target actor.
     *
     * @param targetActor The actor to send the message to.
     */
    void to(@NotNull ActorHandle<? extends M> targetActor);
  }

  /**
   * Operation for requesting a response of a specific type.
   *
   * @param <M> The message type.
   */
  @FunctionalInterface
  interface ResponseRequestOp<M> {

    /**
     * Specifies the expected response type.
     *
     * @param responseType The class of the response type.
     * @param <R>          The response type.
     * @return An operation to request the response from a target actor.
     */
    <R> @NotNull ResponseRequestFromOp<M, R> ofType(@NotNull Class<R> responseType);
  }

  /**
   * Operation for requesting a response from a target actor.
   *
   * @param <M> The message type.
   * @param <R> The response type.
   */
  @FunctionalInterface
  interface ResponseRequestFromOp<M, R> {

    /**
     * Requests the response from the specified target actor.
     *
     * @param targetActor The actor to request the response from.
     * @return A future representing the response.
     */
    @NotNull Future<R> from(@NotNull ActorHandle<? extends M> targetActor);
  }

  /**
   * Stops the specified actor.
   *
   * @param actor The actor to stop.
   * @return A future representing completion of the stop operation.
   */
  @NotNull Future<Done> stop(@NotNull ActorHandle<?> actor);

  /**
   * Sends a message to an actor.
   *
   * @param message The message to send.
   * @param <M>     The message type.
   * @return An operation to specify the target actor.
   */
  <M> @NotNull SendMessageOp<M> send(@NotNull M message);

  /**
   * Forwards a message to an actor.
   *
   * @param message The message to forward.
   * @param <M>     The message type.
   * @return An operation to specify the target actor.
   */
  <M> @NotNull SendMessageOp<M> forward(@NotNull M message);

  /**
   * Requests a response to a message.
   *
   * @param message The message to request a response for.
   * @param <M>     The message type.
   * @return An operation to specify the response type.
   */
  @NotNull <M> ResponseRequestOp<M> requestResponseTo(@NotNull M message);

  /**
   * Pipes a future message to an actor.
   *
   * @param eventualMessage The future message.
   * @param <M1>            The message type.
   * @return An operation to specify the target actor.
   */
  <M1> @NotNull FuturePipeOp<M1> pipeFuture(@NotNull Future<M1> eventualMessage);
}
