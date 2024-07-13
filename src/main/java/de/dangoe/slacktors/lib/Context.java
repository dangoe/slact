package de.dangoe.slacktors.lib;

import java.util.Optional;

public interface Context extends ActorFactory {
    <A extends AbstractActor<M>, M> Optional<ActorHandle<M>> select(
        ActorPath path
    );
}
