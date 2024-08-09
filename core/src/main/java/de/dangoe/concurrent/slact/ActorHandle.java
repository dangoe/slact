package de.dangoe.concurrent.slact;

public interface ActorHandle<M> extends ActorSpawner {

  ActorPath path();
}
