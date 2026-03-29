package de.dangoe.concurrent.slact.persistence;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the interface for a persistence extension that can be registered to provide event store
 * implementations based on partition keys.
 */
public interface PersistenceExtension {

  /**
   * Resolves an event store for the given partition key.
   *
   * @param partitionKey The partition key for which to resolve the event store.
   * @return An <code>Optional</code> containing the resolved {@link EventStore} if available, or an
   * empty <code>Optional</code> if no store is found for the given partition key.
   */
  @NotNull Optional<EventStore> resolveStore(@NotNull PartitionKey partitionKey);


  /**
   * Resolves a snapshot-capable event store for the given partition key. This method is used to
   * obtain an event store that supports snapshotting capabilities, which can be beneficial for
   * actors that need to recover their state more efficiently by loading snapshots instead of
   * replaying all events. The returned <code>Optional</code> will contain a
   * {@link SnapshotCapableEventStore} if a suitable store is found for the given partition key, or
   * it will be empty if no such store is available.
   *
   * @param partitionKey The partition key for which to resolve the snapshot-capable event store.
   * @return An <code>Optional</code> containing the resolved {@link SnapshotCapableEventStore} if
   * available, or an empty <code>Optional</code> if no snapshot-capable store is found for the
   * given partition key.
   */
  @NotNull Optional<SnapshotCapableEventStore> resolveSnapshotCapableStore(
      @NotNull PartitionKey partitionKey);
}
