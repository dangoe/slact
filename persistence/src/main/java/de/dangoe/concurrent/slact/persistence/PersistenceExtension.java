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
   * @param <S> The type of events that the resolved EventStore will manage.
   * @return An <code>Optional</code> containing the resolved {@link EventStore} if available, or an
   * empty <code>Optional</code> if no store is found for the given partition key.
   */
  <S> @NotNull Optional<EventStore<S>> resolveStore(@NotNull PartitionKey key);
}
