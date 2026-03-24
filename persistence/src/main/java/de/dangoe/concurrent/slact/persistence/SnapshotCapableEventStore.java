package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.SnapshotEnvelope.SnapshotEventEnvelope;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * An interface that combines the capabilities of an event store with snapshot support. This allows
 * for managing both the event stream and the snapshot state of an entity within a single interface,
 * providing a cohesive API for persistence operations that involve both events and snapshots.
 *
 * @param <E> The type of domain events that the event store will manage.
 * @param <S> The type of the snapshot payload that will be created and loaded by the snapshot
 *            support.
 */
public interface SnapshotCapableEventStore<E, S> extends EventStore<E> {

  // TODO Support saving snapshots as well

  /**
   * Loads the latest snapshot for the given partition key. The returned snapshot envelope may
   * contain either a snapshot event with the actual snapshot data or a snapshot marker indicating
   * the presence of a snapshot without the data.
   *
   * @param key The partition key for which to load the latest snapshot. This key is used to
   *            identify the specific stream of events from which the snapshot will be loaded.
   * @return A {@link RichFuture} that, when completed, will contain an <code>Optional</code>
   * containing a {@link SnapshotEnvelope} representing the latest snapshot for the given partition
   * key. The snapshot envelope may contain either a snapshot event with the actual snapshot data or
   * a snapshot marker indicating the presence of a snapshot without the data. If no snapshot is
   * available for the given partition key, the returned <code>Optional</code> will be empty.
   */
  @NotNull RichFuture<Optional<SnapshotEventEnvelope<S>>> loadLatestSnapshot(
      @NotNull PartitionKey key);
}
