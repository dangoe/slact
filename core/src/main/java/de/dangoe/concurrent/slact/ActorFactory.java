package de.dangoe.concurrent.slact;

import java.io.Serializable;
import java.util.UUID;

public interface ActorFactory {

    default <A extends AbstractActor<M>, M extends Serializable> ActorHandle<M> register(final ActorCreator<A> actorCreator) {
        return register(UUID.randomUUID().toString(), actorCreator);
    }

    <A extends AbstractActor<M>, M extends Serializable> ActorHandle<M> register(String name, ActorCreator<A> actorCreator);
}
