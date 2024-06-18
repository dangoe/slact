package de.dangoe.slacktors.lib;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface Context extends ActorFactory {

    <A extends AbstractActor<M>, M> Optional<ActorHandle<M>> select(ActorPath path);
}
