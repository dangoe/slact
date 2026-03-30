package de.dangoe.concurrent.slact.persistence.exception;

import de.dangoe.concurrent.slact.persistence.PartitionKey;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when actor state recovery from the event store fails.
 */
public final class RecoveryFailedException extends PersistenceException {

  /**
   * @param partitionKey the partition key whose recovery failed.
   * @param cause        the underlying cause of the failure.
   */
  public RecoveryFailedException(final @NotNull PartitionKey partitionKey,
      final @NotNull Throwable cause) {

    super("Recovery failed for partition key '%s'.".formatted(partitionKey.raw()), cause);
  }
}

