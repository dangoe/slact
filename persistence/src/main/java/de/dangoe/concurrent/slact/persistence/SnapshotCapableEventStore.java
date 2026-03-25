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

  // TODO Support saving snapshots as well

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
}
