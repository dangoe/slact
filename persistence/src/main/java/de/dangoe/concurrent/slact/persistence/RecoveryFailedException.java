package de.dangoe.concurrent.slact.persistence;

import org.jetbrains.annotations.NotNull;

public final class RecoveryFailedException extends RuntimeException {

  public RecoveryFailedException(final @NotNull PartitionKey partitionKey,
      final @NotNull Throwable cause) {

    super("Recovery failed for partition key '%s'.".formatted(partitionKey.value()), cause);
  }
}

