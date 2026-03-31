package de.dangoe.concurrent.slact.persistence.exception;

import de.dangoe.concurrent.slact.persistence.PartitionKey;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when persisting events to the event store fails.
 */
public final class SaveFailedException extends PersistenceException {

  /**
   * Constructs a {@link SaveFailedException} for the given partition key.
   *
   * @param partitionKey the partition key whose save operation failed.
   * @param cause        the underlying cause of the failure.
   */
  public SaveFailedException(final @NotNull PartitionKey partitionKey,
      final @NotNull Throwable cause) {

    super("Saving state failed for partition key '%s'.".formatted(partitionKey.raw()), cause);
  }
}
