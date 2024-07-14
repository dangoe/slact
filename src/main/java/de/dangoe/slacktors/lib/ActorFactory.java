package de.dangoe.slacktors.lib;

import java.util.UUID;
import java.util.function.Supplier;

public interface ActorFactory {
    default <A extends AbstractActor<M>, M> ActorHandle<M> newActor(
        final Supplier<A> initializer
    ) {
        return newActor(UUID.randomUUID().toString(), initializer);
    }

    <A extends AbstractActor<M>, M> ActorHandle<M> newActor(
        String name,
        Supplier<A> initializer
    );
}
