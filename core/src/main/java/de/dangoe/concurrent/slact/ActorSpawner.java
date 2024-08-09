package de.dangoe.concurrent.slact;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public interface ActorSpawner {

  default @NotNull <A extends Actor<M>, M> ActorHandle<M> spawn(
      final @NotNull ActorCreator<A> actorCreator) {
    return spawn(UUID.randomUUID().toString(), actorCreator);
  }

  @NotNull
  <A extends Actor<M>, M> ActorHandle<M> spawn(@NotNull String name,
      @NotNull ActorCreator<A> actorCreator);
}
