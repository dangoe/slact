package de.dangoe.concurrent.slact;

public interface ActorHandle<M> extends ActorRegistry {

    ActorPath path();
}
