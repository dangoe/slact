package de.dangoe.slacktors.lib;

import java.io.Serializable;
import java.util.UUID;
import java.util.function.Supplier;

public interface ActorFactory {

    default <A extends AbstractActor<M>, M extends Serializable> ActorHandle<M> actorOf(final Supplier<A> initializer) {
        return actorOf(UUID.randomUUID().toString(), initializer);
    }

    <A extends AbstractActor<M>, M extends Serializable> ActorHandle<M> actorOf(String name, Supplier<A> initializer);
}
