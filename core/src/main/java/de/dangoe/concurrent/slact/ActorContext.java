package de.dangoe.concurrent.slact;

public interface ActorContext extends ActorRegistry, ActorHandleResolver {

    @FunctionalInterface
    interface SendableMessage<M1> {
        void to(ActorHandle<? extends M1> targetActor);
    }

    <M1> SendableMessage<M1> send(M1 message);

    ActorHandle<?> sender();

    ActorHandle<?> parent();

    ActorHandle<?> self();
}
