// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence.exception;

import de.dangoe.concurrent.slact.persistence.PartitionKey;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when a concurrent write attempt is detected for the same partition key.
 */
public final class ConcurrentWriteException extends PersistenceException {

  /**
   * Constructs a {@link ConcurrentWriteException} for the given partition key.
   *
   * @param partitionKey the partition key on which the concurrent write was detected.
   */
  public ConcurrentWriteException(final @NotNull PartitionKey partitionKey) {
    super("Concurrent write detected for partition key '%s'.".formatted(partitionKey.raw()));
  }
}
