package de.dangoe.concurrent.slact.persistence.testkit;

import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

public final class InMemoryEventStore<E> implements EventStore<E> {

  private final ConcurrentHashMap<String, CopyOnWriteArrayList<EventEnvelope<E>>> store = new ConcurrentHashMap<>();

  private final @NotNull Clock clock;

  public InMemoryEventStore(final @NotNull Clock clock) {
    this.clock = clock;
  }

  @Override
  public @NotNull CompletableFuture<List<EventEnvelope<E>>> loadEvents(
      final @NotNull PartitionKey partitionKey) {

    return CompletableFuture.completedFuture(
        List.copyOf(store.getOrDefault(partitionKey.value(), new CopyOnWriteArrayList<>())));
  }

  @Override
  public @NotNull CompletableFuture<List<EventEnvelope<E>>> appendMultiple(
      final @NotNull PartitionKey partitionKey,
      final @NotNull List<E> events) {

    final var lastOrdering = store.getOrDefault(partitionKey.value(), new CopyOnWriteArrayList<>())
        .stream()
        .mapToLong(EventEnvelope::ordering)
        .max()
        .orElse(0L);

    final var addedEventEnvelopes = events.stream()
        .map(it -> new EventEnvelope<>(lastOrdering + 1, Instant.now(clock), it))
        .toList();

    store.computeIfAbsent(partitionKey.value(), k -> new CopyOnWriteArrayList<>())
        .addAll(addedEventEnvelopes);

    return CompletableFuture.completedFuture(addedEventEnvelopes);
  }
}
