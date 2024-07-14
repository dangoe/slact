package de.dangoe.slacktors.lib;

@FunctionalInterface
public interface ActorCreator<A> {

    A create();
}
