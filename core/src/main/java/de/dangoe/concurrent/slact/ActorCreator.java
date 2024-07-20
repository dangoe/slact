package de.dangoe.concurrent.slact;

@FunctionalInterface
public interface ActorCreator<A> {

    A create();
}
