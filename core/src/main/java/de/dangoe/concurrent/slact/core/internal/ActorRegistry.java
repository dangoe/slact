package de.dangoe.concurrent.slact.core.internal;

import org.jetbrains.annotations.NotNull;

interface ActorRegistry {

  void register(@NotNull ActorWrapper<?> actor);

  void unregister(@NotNull ActorWrapper<?> actor);
}
