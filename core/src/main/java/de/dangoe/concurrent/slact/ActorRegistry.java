package de.dangoe.concurrent.slact;

import org.jetbrains.annotations.NotNull;

interface ActorRegistry {

  void register(@NotNull ActorWrapper<?> actor);

  void unregister(@NotNull ActorWrapper<?> actor);
}
