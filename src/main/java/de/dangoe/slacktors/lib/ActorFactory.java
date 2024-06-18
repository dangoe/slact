package de.dangoe.slacktors.lib;

import java.util.UUID;
import java.util.function.Supplier;

public interface ActorFactory {

    default <A extends AbstractActor<M>, M> ActorHandle<M> actorOf(Supplier<A> factory) {
        return actorOf(UUID.randomUUID().toString(), factory);
    }

    <A extends AbstractActor<M>, M> ActorHandle<M> actorOf(String name, Supplier<A> factory);
}
