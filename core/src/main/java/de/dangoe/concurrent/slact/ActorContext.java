package de.dangoe.concurrent.slact;

import java.util.Optional;
import java.util.concurrent.Future;

public interface ActorContext extends ActorSpawner, ActorHandleResolver {

  @FunctionalInterface
  interface PreparedSendMessageOp<M> {

    void to(ActorHandle<? extends M> targetActor);
  }

  @FunctionalInterface
  interface PreparedSendMessageWithResponseRequestOp<M, R> {

    Future<R> from(ActorHandle<? extends M> targetActor);
  }

  @FunctionalInterface
  interface PreparedForwardMessageOp<M> {

    void to(ActorHandle<? extends M> targetActor);
  }

  <M> PreparedSendMessageOp<M> send(M message);

  <M> void respondWith(M message);

  <M> PreparedForwardMessageOp<M> forward(M message);

  String messageId();

  Optional<String> correlationMessageId();

  ActorHandle<?> sender();

  ActorHandle<?> parent();

  ActorHandle<?> self();

  <M1> FuturePipeOp<M1> pipeFuture(Future<M1> eventualMessage);
}
