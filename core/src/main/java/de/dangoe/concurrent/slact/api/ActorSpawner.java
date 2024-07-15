package de.dangoe.concurrent.slact.api;

import java.util.UUID;

public interface ActorSpawner {

    default <A extends Actor<M>, M> ActorHandle<M> spawn(final ActorCreator<A> actorCreator) {
        return spawn(UUID.randomUUID().toString(), actorCreator);
    }

    <A extends Actor<M>, M> ActorHandle<M> spawn(String name, ActorCreator<A> actorCreator);
}
