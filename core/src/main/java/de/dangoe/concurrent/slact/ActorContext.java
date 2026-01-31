package de.dangoe.concurrent.slact;

import org.jetbrains.annotations.NotNull;

public interface ActorContext extends ActorRuntime {

  @NotNull ActorHandle<?> parent();

  @NotNull ActorHandle<?> self();

  @NotNull ActorHandle<?> sender();

  <M> void respondWith(@NotNull M message);
}
