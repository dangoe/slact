package de.dangoe.slacktors.lib;

public interface ActorContext extends ActorFactory, ActorSelector {

    ActorPath path();
}
