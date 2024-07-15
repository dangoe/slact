package de.dangoe.concurrent.slact.api;

@FunctionalInterface
public interface ActorCreator<A> {

    A create();
}
