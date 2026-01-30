package de.dangoe.concurrent.slact;

import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public interface ActorContext extends ActorSpawner, ActorHandleResolver {

  @FunctionalInterface
  interface PreparedSendMessageOp<M> {

    void to(@NotNull ActorHandle<? extends M> targetActor);
  }

  @FunctionalInterface
  interface IntermediateSendMessageWithResponseRequestOp<M> {

    <R> @NotNull CompletableSendMessageWithResponseRequestOp<M, R> ofType(
        @NotNull Class<R> responseType);
  }

  @FunctionalInterface
  interface CompletableSendMessageWithResponseRequestOp<M, R> {

    @NotNull Future<R> from(@NotNull ActorHandle<? extends M> targetActor);
  }

  @FunctionalInterface
  interface PreparedForwardMessageOp<M> {

    void to(@NotNull ActorHandle<? extends M> targetActor);
  }

  @NotNull <M> PreparedSendMessageOp<M> send(@NotNull M message);

  <M> void respondWith(@NotNull M message);

  @NotNull <M> PreparedForwardMessageOp<M> forward(@NotNull M message);

  void exterminate(@NotNull ActorHandle<?> actor);

  @NotNull ActorHandle<?> sender();

  @NotNull ActorHandle<?> parent();

  @NotNull ActorHandle<?> self();

  @NotNull <M1> FuturePipeOp<M1> pipeFuture(@NotNull Future<M1> eventualMessage);
}
