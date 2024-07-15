package de.dangoe.concurrent.slact.api;

public interface ActorHandle<M> extends ActorSpawner {

    ActorPath path();
}
