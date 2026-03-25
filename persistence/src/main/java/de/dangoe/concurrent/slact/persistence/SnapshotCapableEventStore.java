package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * An extension of the EventStore interface that adds support for loading snapshots. This interface
 * defines the contract for an event store that can manage both events and snapshots, allowing for
 * more efficient recovery of state by loading the latest snapshot and then applying only the events
 * that occurred after the snapshot was taken.
 *
 * @param <E> The type of domain events that the event store will manage.
 * @param <S> The type of snapshot that the snapshots will contain.
 */
public interface SnapshotCapableEventStore<E, S> extends EventStore<E> {

  /**
   * Loads the latest snapshot for the given partition key, if available. The returned future will
   * complete with an optional containing the snapshot if a snapshot is available, or an empty
   * optional if no snapshot is available for the given partition key. The future will complete
   * exceptionally if an error occurs while loading the snapshot.
   *
   * @param key The partition key for which to load the latest snapshot. This key is used to
   *            identify the specific
   * @return A {@link RichFuture} that, when completed, will contain an {@link Optional} with the
   * latest snapshot for the given partition key, or an empty optional if no snapshot is available.
   */
  @NotNull RichFuture<Optional<SnapshotEnvelope<S>>> loadLatestSnapshot(@NotNull PartitionKey key);

  /**
   * Saves a snapshot for the given partition key. The returned future will complete with the
   * snapshot envelope containing the saved snapshot and its associated metadata, such as the
   * ordering value and timestamp.
   *
   * @param key                  The partition key for which to save the snapshot. This key is used
   *                             to identify the specific entity or aggregate for which the snapshot
   *                             is being saved. It allows the event store to associate the snapshot
   *                             with the correct event stream and ensures that the snapshot can be
   *                             retrieved correctly when needed.
   * @param lastSnapshotOrdering The ordering value of the last snapshot that was saved for the
   *                             given partition key. This value is used to ensure that snapshots
   *                             are saved in the correct order and to detect any potential
   *                             concurrency issues when multiple snapshots are being saved for the
   *                             same partition key.
   * @param appliedUpToOrdering  The ordering value up to which events have been applied to the
   *                             snapshot state. This indicates the point in the event stream up to
   *                             which the snapshot reflects the state of the entity. It is used to
   *                             ensure that the snapshot is consistent with the events that have
   *                             been applied and can be used to determine which events need to be
   *                             applied when restoring the state from the snapshot.
   * @param snapshot             The snapshot data to be saved. This represents the state of the
   *                             entity at a specific point in time and can be used for efficient
   *                             recovery without needing to replay all events from the beginning of
   *                             the event stream.
   * @return A {@link RichFuture} that, when completed, will contain the {@link SnapshotEnvelope}
   * with the saved snapshot and its associated metadata. The future will complete exceptionally if
   * an error occurs while saving the snapshot.
   */
  @NotNull RichFuture<SnapshotEnvelope<S>> saveSnapshot(@NotNull PartitionKey key,
      long lastSnapshotOrdering, long appliedUpToOrdering, @NotNull S snapshot);
}
