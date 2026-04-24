// SPDX-License-Identifier: MIT OR Apache-2.0

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
   * @param path the actor path.
   * @param <M>  the message type.
   * @return an optional actor handle.
   */
  @NotNull
  <M> Optional<ActorHandle<M>> resolve(@NotNull ActorPath path);
}
