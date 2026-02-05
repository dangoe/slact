package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

public interface ActorHandle<M> extends ActorSpawner {

  @NotNull ActorPath path();

  void send(@NotNull M message);
}
