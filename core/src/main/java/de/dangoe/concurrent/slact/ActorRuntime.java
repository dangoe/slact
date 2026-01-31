package de.dangoe.concurrent.slact;

import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public interface ActorRuntime extends ActorHandleResolver, ActorSpawner {

  @FunctionalInterface
  interface SendMessageOp<M> {

    void to(@NotNull ActorHandle<? extends M> targetActor);
  }

  @FunctionalInterface
  interface ResponseRequestOp<M> {

    <R> @NotNull ResponseRequestFromOp<M, R> ofType(@NotNull Class<R> responseType);
  }

  @FunctionalInterface
  interface ResponseRequestFromOp<M, R> {

    @NotNull Future<R> from(@NotNull ActorHandle<? extends M> targetActor);
  }

  @NotNull Future<Done> stop(@NotNull ActorHandle<?> actor);

  <M> @NotNull SendMessageOp<M> send(@NotNull M message);

  <M> @NotNull SendMessageOp<M> forward(@NotNull M message);

  @NotNull <M> ActorContext.ResponseRequestOp<M> requestResponseTo(@NotNull M message);

  <M1> @NotNull FuturePipeOp<M1> pipeFuture(@NotNull Future<M1> eventualMessage);
}
