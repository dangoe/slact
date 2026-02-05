package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

public interface ActorContext<M> extends ActorRuntime {

  @NotNull ActorHandle<?> parent();

  @NotNull ActorHandle<M> self();

  @NotNull ActorHandle<?> sender();

  <M1> void respondWith(@NotNull M1 message);
}
