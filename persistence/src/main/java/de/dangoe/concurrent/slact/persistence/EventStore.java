package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the contract for an event store that allows loading and appending events associated with
 * a specific partition key.
 *
 * @param <E> The type of domain events that the event store will manage.
 */
public interface EventStore<E> {

  /**
   * Loads the events associated with the given partition key. The returned list of events is
   * ordered by their ordering value, ensuring that the sequence of events is maintained.
   *
   * @param partitionKey The partition key for which to load the events. This key is used to
   *                     identify the specific stream of events to retrieve.
   * @return A {@link RichFuture}  that, when completed, will contain a list of
   * {@link EventEnvelope} objects representing the events associated with the given partition key.
   */
  @NotNull RichFuture<List<EventEnvelope<E>>> loadEvents(@NotNull PartitionKey partitionKey);

  /**
   * Appends a single event to the event store for the specified partition key.
   *
   * @param partitionKey    The partition key for which to append the event. This key is used to
   *                        identify the specific stream of events to which the new event will be
   *                        added.
   * @param lastMaxOrdering The last known maximum ordering value for the events in the partition.
   *                        This is used to detect any potential concurrency issues.
   * @param event           The event to be appended to the event store. This is the actual event
   *                        data of type <code>E</code> that will be stored.
   * @return A {@link RichFuture} that, when completed, will contain an {@link EventEnvelope}
   * representing the appended event, including its ordering and timestamp.
   * @throws ConcurrentWriteException if a concurrency conflict is detected, i.e., if the last
   *                                  known maximum ordering value does not match the current
   *                                  maximum ordering in the event store.
   */
  default @NotNull RichFuture<EventEnvelope<E>> append(final @NotNull PartitionKey partitionKey,
      long lastMaxOrdering, @NotNull E event) {

    return appendMultiple(partitionKey, lastMaxOrdering, List.of(event)).thenApply(List::getFirst);
  }

  /**
   * Appends multiple events to the event store for the specified partition key. The events will be
   * appended in the order they are provided in the list.
   *
   * @param partitionKey    The partition key for which to append the events. This key is used to
   *                        identify the specific stream of events to which the new events will be
   *                        added.
   * @param lastMaxOrdering The last known maximum ordering value for the events in the partition.
   *                        This is used to detect any potential concurrency issues.
   * @param events          The list of events to be appended to the event store. These are the
   *                        actual event data of type <code>E</code> that will be stored.
   * @return A {@link RichFuture} that, when completed, will contain a list of {@link EventEnvelope}
   * objects representing the appended events, including their ordering and timestamps.
   * @throws ConcurrentWriteException if a concurrency conflict is detected, i.e., if the last
   *                                  known maximum ordering value does not match the current
   *                                  maximum ordering in the event store.
   */
  @NotNull RichFuture<List<EventEnvelope<E>>> appendMultiple(@NotNull PartitionKey partitionKey,
      long lastMaxOrdering, @NotNull List<E> events);
}
