package de.dangoe.concurrent.slact.persistence;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a partition key used to identify a specific partition in the event store. The
 * partition key is a non-null, non-blank string that serves as an identifier for grouping related
 * events together.
 *
 * @param value The string value of the partition key. It must not be <code>null</code> or blank.
 */
public record PartitionKey(@NotNull String value) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  public PartitionKey {

    Objects.requireNonNull(value, "Value must not be null!");

    if (value.isBlank()) {
      throw new IllegalArgumentException("Value must not be blank!");
    }
  }

  /**
   * Creates a new partition key instance with the given string value.
   *
   * @param value The string value to be used as the partition key. It must not be <code>null</code>
   *              or blank.
   * @return A new instance of partition key with the specified value.
   */
  public static @NotNull PartitionKey of(final @NotNull String value) {
    return new PartitionKey(value);
  }
}
