package de.dangoe.concurrent.slact.persistence;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record PartitionKey(@NotNull String value) {

  public PartitionKey {

    Objects.requireNonNull(value, "Value must not be null!");

    if (value.isBlank()) {
      throw new IllegalArgumentException("Partition key value must not be blank.");
    }
  }

  public static @NotNull PartitionKey of(final @NotNull String value) {
    return new PartitionKey(value);
  }
}
