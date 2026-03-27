package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;

public class InMemoryEventStore implements EventStore {

  private record StoreKey(@NotNull Class<?> partitionKeyType,
                          @NotNull String value) {

  }

  protected final @NotNull Clock clock;

  private final @NotNull ConcurrentHashMap<StoreKey, CopyOnWriteArrayList<EventEnvelope<?>>> events = new ConcurrentHashMap<>();

  public InMemoryEventStore(final @NotNull Clock clock) {
    this.clock = clock;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E> @NotNull RichFuture<List<EventEnvelope<E>>> loadEvents(
      final @NotNull PartitionKey<E> partitionKey, final long fromOrdering) {

    return RichFuture.of(CompletableFuture.completedFuture(List.copyOf(
        events.getOrDefault(new StoreKey(partitionKey.getClass(), partitionKey.value()),
                new CopyOnWriteArrayList<>()).stream().filter(it -> it.ordering() >= fromOrdering)
            .map(it -> (EventEnvelope<E>) it).toList())));
  }

  @Override
  public <E> @NotNull RichFuture<List<EventEnvelope<E>>> appendMultiple(
      final @NotNull PartitionKey<E> partitionKey, final long lastMaxOrdering,
      final @NotNull List<E> events) throws ConcurrentWriteException {

    final var storeKey = new StoreKey(partitionKey.getClass(), partitionKey.value());

    final var lastOrdering = this.events.getOrDefault(storeKey, new CopyOnWriteArrayList<>())
        .stream().mapToLong(EventEnvelope::ordering).max().orElse(-1L);

    if (lastOrdering > lastMaxOrdering) {
      throw new ConcurrentWriteException(partitionKey);
    }

    final var orderingCounter = new AtomicLong(lastOrdering + 1);

    final var addedEventEnvelopes = events.stream()
        .map(it -> new EventEnvelope<>(orderingCounter.getAndIncrement(), Instant.now(clock), it))
        .toList();

    this.events.computeIfAbsent(storeKey, k -> new CopyOnWriteArrayList<>())
        .addAll(addedEventEnvelopes);

    return RichFuture.of(CompletableFuture.completedFuture(addedEventEnvelopes));
  }
}
