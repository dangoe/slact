package de.dangoe.concurrent.slact.core;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Spawns actors in the actor system.
 */
public interface ActorSpawner {

  /**
   * Spawns an actor with a random name.
   *
   * @param actorCreator The actor creator.
   * @param <A>          The actor type.
   * @param <M>          The message type.
   * @return The actor handle.
   */
  default @NotNull <A extends Actor<M>, M> ActorHandle<M> spawn(
      final @NotNull ActorCreator<A, M> actorCreator) {
    return spawn(UUID.randomUUID().toString(), actorCreator);
  }

  /**
   * Spawns an actor with the given name.
   *
   * @param name         The actor name.
   * @param actorCreator The actor creator.
   * @param <A>          The actor type.
   * @param <M>          The message type.
   * @return The actor handle.
   */
  @NotNull
  <A extends Actor<M>, M> ActorHandle<M> spawn(@NotNull String name,
      @NotNull ActorCreator<A, M> actorCreator);
}
