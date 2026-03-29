package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SnapshotCapableInMemoryEventStore extends InMemoryEventStore implements
    SnapshotCapableEventStore {

  private record SnapshotStoreKey(@NotNull Class<?> partitionKeyType, @NotNull String value) {

  }

  private final @NotNull ConcurrentHashMap<SnapshotStoreKey, SnapshotEnvelope<?>> snapshots = new ConcurrentHashMap<>();

  public SnapshotCapableInMemoryEventStore(final @NotNull Clock clock) {
    super(clock);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <S> @NotNull RichFuture<Optional<SnapshotEnvelope<S>>> loadLatestSnapshot(
      final @NotNull PartitionKey partitionKey, final @NotNull Class<S> snapshotType) {

    return RichFuture.of(CompletableFuture.completedFuture(Optional.ofNullable(
        (SnapshotEnvelope<S>) snapshots.get(
            new SnapshotStoreKey(partitionKey.getClass(), partitionKey.raw())))));
  }

  @Override
  public <S> @NotNull RichFuture<SnapshotEnvelope<S>> saveSnapshot(
      final @NotNull PartitionKey partitionKey, final @Nullable Long lastSnapshotOrdering,
      long appliedUpToOrdering, final @NotNull S snapshot) {

    final var snapshotStoreKey = new SnapshotStoreKey(partitionKey.getClass(),
        partitionKey.raw());

    final var existingSnapshot = this.snapshots.get(snapshotStoreKey);
    final var lastOrdering = existingSnapshot != null ? existingSnapshot.ordering() : 0;

    if (lastSnapshotOrdering != null && lastOrdering > lastSnapshotOrdering) {
      throw new ConcurrentWriteException(partitionKey);
    }

    final var orderingCounter = new AtomicLong(lastOrdering + 1);

    final var addedSnapshotEnvelope = new SnapshotEnvelope<>(orderingCounter.getAndIncrement(),
        appliedUpToOrdering, Instant.now(clock), snapshot);

    this.snapshots.put(snapshotStoreKey, addedSnapshotEnvelope);

    return RichFuture.of(CompletableFuture.completedFuture(addedSnapshotEnvelope));
  }
}
