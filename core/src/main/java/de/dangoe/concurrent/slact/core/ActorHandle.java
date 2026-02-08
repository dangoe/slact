package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

public interface ActorHandle<M> {

  @NotNull ActorPath path();

  void send(@NotNull M message);
}
