package de.dangoe.concurrent.slact.persistence.exception;

import de.dangoe.concurrent.slact.persistence.PartitionKey;
import org.jetbrains.annotations.NotNull;

public final class RecoveryFailedException extends PersistenceException {

  public RecoveryFailedException(final @NotNull PartitionKey<?> partitionKey,
      final @NotNull Throwable cause) {

    super("Recovery failed for partition key '%s'.".formatted(partitionKey.value()), cause);
  }
}

