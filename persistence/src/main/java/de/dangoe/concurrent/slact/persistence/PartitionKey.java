package de.dangoe.concurrent.slact.persistence;

import java.io.Serializable;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies a specific event stream in the event store. Each actor type defines its own concrete
 * implementation of this interface (typically as a record), making the implementation class itself
 * the stream-type discriminator. Combined with {@link #value()} (the instance identifier), this
 * uniquely identifies an event stream across all actor types — even when multiple actor types share
 * the same event type.
 *
 * <p>Implementations are responsible for validating that {@link #value()} is non-null and
 * non-blank.
 *
 * @param <E> The type of events stored in the stream identified by this partition key.
 */
public interface PartitionKey<E> extends Serializable {

  /**
   * Returns the class of the event type stored in this event stream.
   *
   * @return The event type class; never {@code null}.
   */
  @NotNull Class<E> eventType();

  /**
   * Returns the instance identifier for this event stream.
   *
   * @return A non-null, non-blank string that uniquely identifies the stream instance within its
   * stream type.
   */
  @NotNull String value();
}
