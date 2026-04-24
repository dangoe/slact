// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core;

/**
 * Container for managing actors and their runtime.
 */
public interface SlactContainer extends ActorRuntime, AutoCloseable {

  /**
   * Shuts down the container and all managed actors.
   *
   * @throws Exception if shutdown fails.
   */
  void shutdown() throws Exception;

  /**
   * Checks if the container is stopped.
   *
   * @return {@code true} if stopped, {@code false} otherwise.
   */
  boolean isStopped();
}
