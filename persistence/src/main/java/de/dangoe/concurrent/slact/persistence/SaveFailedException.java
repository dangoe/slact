package de.dangoe.concurrent.slact.persistence;

import org.jetbrains.annotations.NotNull;

public final class SaveFailedException extends RuntimeException {

  public SaveFailedException(final @NotNull PartitionKey partitionKey,
      final @NotNull Throwable cause) {

    super("Saving state failed for partition key '%s'.".formatted(partitionKey.value()), cause);
  }
}

