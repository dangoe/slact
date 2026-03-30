package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the contract for loading and appending domain events for a given partition.
 *
 * <p>Example — load all events and append a new one:
 * <pre>{@code
 * EventStore store = ...;
 * PartitionKey key = PartitionKey.of("order-42");
 *
 * List<EventEnvelope<OrderEvent>> past = store.loadEvents(key).join();
 * long next = past.stream().mapToLong(EventEnvelope::ordering).max().orElse(-1L);
 *
 * store.append(key, next, new OrderPlaced("item-7")).join();
 * }</pre>
 */
public interface EventStore {

  /**
   * Loads all events for the given partition key, ordered by their ordering value.
   *
   * @param <E>          the event type.
   * @param partitionKey the partition to load events from.
   * @return a future completing with an ordering-sorted list of all events for the partition.
   */
  default <E> @NotNull RichFuture<List<EventEnvelope<E>>> loadEvents(
      final @NotNull PartitionKey partitionKey) {
    return loadEvents(partitionKey, 0);
  }

  /**
   * Loads events for the given partition key starting at the specified ordering position.
   *
   * @param <E>          the event type.
   * @param partitionKey the partition to load events from.
   * @param fromOrdering only events with ordering ≥ this value are returned.
   * @return a future completing with an ordering-sorted list of matching events.
   */
  <E> @NotNull RichFuture<List<EventEnvelope<E>>> loadEvents(@NotNull PartitionKey partitionKey,
      long fromOrdering);

  /**
   * Appends a single event to the event store for the specified partition key.
   *
   * @param <E>             the event type.
   * @param partitionKey    the partition to append the event to.
   * @param lastMaxOrdering the caller's last known max ordering; used for optimistic concurrency.
   * @param event           the event to append.
   * @return a future completing with the persisted {@link EventEnvelope}.
   * @throws ConcurrentWriteException if another writer advanced the ordering since
   *                                  {@code lastMaxOrdering} was read.
   */
  default <E> @NotNull RichFuture<EventEnvelope<E>> append(
      final @NotNull PartitionKey partitionKey, long lastMaxOrdering, @NotNull E event) {

    return appendMultiple(partitionKey, lastMaxOrdering, List.of(event)).thenApply(List::getFirst);
  }

  /**
   * Appends multiple events atomically to the event store for the specified partition key.
   *
   * @param <E>             the event type.
   * @param partitionKey    the partition to append the events to.
   * @param lastMaxOrdering the caller's last known max ordering; used for optimistic concurrency.
   * @param events          the list of events to append, in order.
   * @return a future completing with the persisted {@link EventEnvelope} list.
   * @throws ConcurrentWriteException if another writer advanced the ordering since
   *                                  {@code lastMaxOrdering} was read.
   */
  <E> @NotNull RichFuture<List<EventEnvelope<E>>> appendMultiple(
      @NotNull PartitionKey partitionKey, long lastMaxOrdering, @NotNull List<E> events);
}
