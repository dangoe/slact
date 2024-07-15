package de.dangoe.concurrent.slact;

import java.util.Optional;

public interface ActorContext extends ActorRegistry, ActorHandleResolver {

    @FunctionalInterface
    interface SendableAskMessage<M1> {
        void to(ActorHandle<? extends M1> targetActor);
    }

    @FunctionalInterface
    interface SendableMessage<M1> {
        void to(ActorHandle<? extends M1> targetActor);
    }

    @FunctionalInterface
    interface ForwardableMessage<M1> {
        void to(ActorHandle<? extends M1> targetActor);
    }

    <M1> SendableMessage<M1> send(M1 message);

    <M1> ForwardableMessage<M1> forward(M1 message);

    String messageId();

    Optional<String> correlationMessageId();

    ActorHandle<?> sender();

    ActorHandle<?> parent();

    ActorHandle<?> self();
}
