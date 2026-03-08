package de.dangoe.concurrent.slact.persistence;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the contract for an event store that allows loading and appending events associated with
 * a specific partition key.
 *
 * @param <E> The type of events that the event store will manage.
 */
public interface EventStore<E> {

  /**
   * Loads the events associated with the given partition key. The returned list of events is
   * ordered by their ordering value, ensuring that the sequence of events is maintained.
   *
   * @param partitionKey The partition key for which to load the events. This key is used to
   *                     identify the specific stream of events to retrieve.
   * @return A CompletableFuture that, when completed, will contain a list of EventEnvelope objects
   * representing the events associated with the given partition key.
   */
  @NotNull CompletableFuture<List<EventEnvelope<E>>> loadEvents(@NotNull PartitionKey partitionKey);

  /**
   * Appends a single event to the event store for the specified partition key.
   *
   * @param partitionKey The partition key for which to append the event. This key is used to
   *                     identify the specific stream of events to which the new event will be
   *                     added.
   * @param event        The event to be appended to the event store. This is the actual event data
   *                     of type <code>E</code> that will be stored.
   * @return A CompletableFuture that, when completed, will contain an EventEnvelope representing
   * the appended event, including its ordering and timestamp.
   */
  default @NotNull CompletableFuture<EventEnvelope<E>> append(
      final @NotNull PartitionKey partitionKey,
      @NotNull E event) {

    return appendMultiple(partitionKey, List.of(event)).thenApply(List::getFirst);
  }

  /**
   * Appends multiple events to the event store for the specified partition key. The events will be
   * appended in the order they are provided in the list.
   *
   * @param partitionKey The partition key for which to append the events. This key is used to
   *                     identify the specific stream of events to which the new events will be
   *                     added.
   * @param events       The list of events to be appended to the event store. These are the actual
   *                     event data of type <code>E</code> that will be stored.
   * @return A CompletableFuture that, when completed, will contain a list of EventEnvelope objects
   * representing the appended events, including their ordering and timestamps.
   */
  @NotNull CompletableFuture<List<EventEnvelope<E>>> appendMultiple(
      @NotNull PartitionKey partitionKey,
      @NotNull List<E> events);
}
