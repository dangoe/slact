package de.dangoe.concurrent.slact.core;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a mechanism to resolve actor handles by their path.
 */
public interface ActorHandleResolver {

  /**
   * Resolves an actor handle for the given path.
   *
   * @param path The actor path.
   * @param <M>  The message type.
   * @return An optional actor handle.
   */
  @NotNull
  <M> Optional<ActorHandle<M>> resolve(@NotNull ActorPath path);
}
