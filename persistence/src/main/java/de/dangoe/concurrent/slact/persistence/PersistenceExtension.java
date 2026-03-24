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
   * @param key The partition key for which to resolve the event store.
   * @param <E> The type of domain events that the resolved EventStore will manage.
   * @param <S> The type of snapshot payloads. Use {@link SnapshotPayload.None} for stores that do
   *            not support snapshotting.
   * @return An <code>Optional</code> containing the resolved {@link EventStore} if available, or an
   * empty <code>Optional</code> if no store is found for the given partition key.
   */
  <E, S extends SnapshotPayload> @NotNull Optional<EventStore<E, S>> resolveStore(
      @NotNull PartitionKey key);
}
