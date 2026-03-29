package de.dangoe.concurrent.slact.persistence.exception;

import de.dangoe.concurrent.slact.persistence.PartitionKey;
import org.jetbrains.annotations.NotNull;

public final class ConcurrentWriteException extends PersistenceException {

  public ConcurrentWriteException(final @NotNull PartitionKey partitionKey) {
    super("Concurrent write detected for partition key '%s'.".formatted(partitionKey.raw()));
  }
}
