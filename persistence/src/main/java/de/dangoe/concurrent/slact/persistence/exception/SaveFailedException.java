package de.dangoe.concurrent.slact.persistence.exception;

import de.dangoe.concurrent.slact.persistence.PartitionKey;
import org.jetbrains.annotations.NotNull;

public final class SaveFailedException extends PersistenceException {

  public SaveFailedException(final @NotNull PartitionKey partitionKey,
      final @NotNull Throwable cause) {

    super("Saving state failed for partition key '%s'.".formatted(partitionKey.raw()), cause);
  }
}
