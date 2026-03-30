package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension of the EventStore interface that adds support for loading snapshots. This interface
 * defines the contract for an event store that can manage both events and snapshots, allowing for
 * more efficient recovery of state by loading the latest snapshot and then applying only the events
 * that occurred after the snapshot was taken.
 */
public interface SnapshotCapableEventStore extends EventStore {

  /**
   * Loads the latest snapshot for the given partition key, if available. The returned future will
   * complete with an optional containing the snapshot if a snapshot is available, or an empty
   * optional if no snapshot is available for the given partition key. The future will complete
   * exceptionally if an error occurs while loading the snapshot.
   *
   * @param partitionKey the partition key identifying the event stream whose snapshot should be
   *                     loaded.
   * @param snapshotType the class used to deserialize the snapshot.
   * @param <S>          the snapshot type.
   * @return a future completing with the latest snapshot envelope, or empty if no snapshot exists.
   */
  <S> @NotNull RichFuture<Optional<SnapshotEnvelope<S>>> loadLatestSnapshot(
      @NotNull PartitionKey partitionKey, @NotNull Class<S> snapshotType);

  /**
   * Saves a snapshot for the given partition key.
   *
   * @param partitionKey         the partition key to associate with this snapshot.
   * @param lastSnapshotOrdering the ordering of the previous snapshot, or {@code null} if none.
   * @param appliedUpToOrdering  the highest event ordering already reflected in the snapshot
   *                             state.
   * @param snapshot             the snapshot data representing the entity state.
   * @param <S>                  the snapshot type.
   * @return a future completing with the persisted {@link SnapshotEnvelope}.
   */
  <S> @NotNull RichFuture<SnapshotEnvelope<S>> saveSnapshot(@NotNull PartitionKey partitionKey,
      @Nullable Long lastSnapshotOrdering, long appliedUpToOrdering, @NotNull S snapshot);
}
