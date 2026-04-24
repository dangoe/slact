// SPDX-License-Identifier: MIT OR Apache-2.0

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
   * @param partitionKey the partition key for which to resolve the event store.
   * @return an {@link Optional} containing the resolved {@link EventStore}, or empty if no store is
   * found
   */
  @NotNull Optional<EventStore> resolveStore(@NotNull PartitionKey partitionKey);


  /**
   * Resolves a snapshot-capable event store for the given partition key.
   *
   * @param partitionKey the partition key for which to resolve the snapshot-capable event store.
   * @return an {@link java.util.Optional} containing the resolved
   * {@link SnapshotCapableEventStore}, or empty if none is available.
   */
  @NotNull Optional<SnapshotCapableEventStore> resolveSnapshotCapableStore(
      @NotNull PartitionKey partitionKey);
}
