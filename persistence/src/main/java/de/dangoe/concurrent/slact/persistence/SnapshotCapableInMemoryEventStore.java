package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SnapshotCapableInMemoryEventStore extends InMemoryEventStore implements
    SnapshotCapableEventStore {

  private record SnapshotStoreKey(@NotNull String raw) {

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
            new SnapshotStoreKey(partitionKey.raw())))));
  }

  @Override
  public <S> @NotNull RichFuture<SnapshotEnvelope<S>> saveSnapshot(
      final @NotNull PartitionKey partitionKey, final @Nullable Long lastSnapshotOrdering,
      long appliedUpToOrdering, final @NotNull S snapshot) {

    final var snapshotStoreKey = new SnapshotStoreKey(partitionKey.raw());

    // Mirror the Postgres dialect: ordering = (lastSnapshotOrdering ?? 0) + 1.
    // Using 0 as the "no prior snapshot" base matches the SQL sequence behaviour where the
    // first auto-assigned ordering is 1 when lastSnapshotOrdering is null.
    final long newOrdering = (lastSnapshotOrdering != null ? lastSnapshotOrdering : 0L) + 1;

    // Detect a concurrent write: if another writer has already stored a snapshot at or beyond
    // newOrdering the proposed write would overwrite or be out-of-order.
    final var existingSnapshot = this.snapshots.get(snapshotStoreKey);
    final long existingOrdering = existingSnapshot != null ? existingSnapshot.ordering() : -1L;

    if (existingOrdering >= newOrdering) {
      throw new ConcurrentWriteException(partitionKey);
    }

    final var addedSnapshotEnvelope = new SnapshotEnvelope<>(newOrdering,
        appliedUpToOrdering, Instant.now(clock), snapshot);

    this.snapshots.put(snapshotStoreKey, addedSnapshotEnvelope);

    return RichFuture.of(CompletableFuture.completedFuture(addedSnapshotEnvelope));
  }
}
