package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ActorCreator<A extends Actor<M>, M> {

  @NotNull A create();
}
