// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * Common interface for entries in an event log, providing ordering position and timestamp.
 */
public interface EventLogEntryLike {

  /**
   * Returns the zero-based position of this entry within its partition.
   *
   * @return the ordering index.
   */
  long ordering();

  /**
   * Returns the time this entry was persisted.
   *
   * @return the persistence timestamp.
   */
  @NotNull Instant timestamp();
}
