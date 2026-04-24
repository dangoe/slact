// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core;

/**
 * Represents a cancellable operation, such as a scheduled task.
 */
public interface Cancellable {

  /**
   * Cancels the operation.
   */
  void cancel();
}
