package de.dangoe.concurrent.slact.api;

import java.util.concurrent.Future;

public interface ActorHandle<M> extends ActorSpawner {

    ActorPath path();
}
