package de.dangoe.concurrent.slact.persistence.testkit;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;

public final class InMemoryEventStore<E> implements EventStore<E> {

  private final ConcurrentHashMap<String, CopyOnWriteArrayList<EventEnvelope<E>>> store = new ConcurrentHashMap<>();

  private final @NotNull Clock clock;

  public InMemoryEventStore(final @NotNull Clock clock) {
    this.clock = clock;
  }

  @Override
  public @NotNull RichFuture<List<EventEnvelope<E>>> loadEvents(
      final @NotNull PartitionKey partitionKey) {

    return RichFuture.of(CompletableFuture.completedFuture(
        List.copyOf(store.getOrDefault(partitionKey.value(), new CopyOnWriteArrayList<>()))));
  }

  @Override
  public @NotNull RichFuture<List<EventEnvelope<E>>> appendMultiple(
      final @NotNull PartitionKey partitionKey, final long lastMaxOrdering,
      final @NotNull List<E> events) throws ConcurrentWriteException {

    final var lastOrdering = store.getOrDefault(partitionKey.value(), new CopyOnWriteArrayList<>())
        .stream().mapToLong(EventEnvelope::ordering).max().orElse(-1L);

    if (lastOrdering > lastMaxOrdering) {
      throw new ConcurrentWriteException(partitionKey);
    }

    final var orderingCounter = new AtomicLong(lastOrdering + 1);

    final var addedEventEnvelopes = events.stream()
        .map(it -> new EventEnvelope<>(orderingCounter.getAndIncrement(), Instant.now(clock), it))
        .toList();

    store.computeIfAbsent(partitionKey.value(), k -> new CopyOnWriteArrayList<>())
        .addAll(addedEventEnvelopes);

    return RichFuture.of(CompletableFuture.completedFuture(addedEventEnvelopes));
  }
}
