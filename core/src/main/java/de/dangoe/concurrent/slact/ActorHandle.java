package de.dangoe.concurrent.slact;

import org.jetbrains.annotations.NotNull;

public interface ActorHandle<M> extends ActorSpawner {

  @NotNull ActorPath path();
}
