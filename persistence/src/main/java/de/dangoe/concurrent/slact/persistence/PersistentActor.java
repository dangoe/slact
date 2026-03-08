package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import de.dangoe.concurrent.slact.persistence.exception.RecoveryFailedException;
import de.dangoe.concurrent.slact.persistence.exception.SaveFailedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A base class for actors that require persistence capabilities. This class provides mechanisms for
 * recovering state from an event store and persisting new events. It handles the recovery process
 * during actor startup and allows derived classes to define their specific behavior after recovery
 * is complete.
 *
 * @param <M> The type of messages that the actor will process.
 * @param <E> The type of events that the actor will persist and recover.
 */
public abstract class PersistentActor<M, E> extends Actor<M> {

  private record RecoveryResultMessage<E>(@NotNull List<E> events) {

  }

  private record RecoveryFailureMessage(@NotNull PartitionKey partitionKey,
                                        @NotNull Throwable cause) {

  }

  // Ensured by onStart() before any message is processed
  @SuppressWarnings("NotNullFieldNotInitialized")
  private @NotNull EventStore<E> eventStore;

  private @NotNull List<EventEnvelope<E>> events;

  protected PersistentActor() {
    this.events = new ArrayList<>();
  }

  @Override
  public final void onStart() {

    final var partitionKey = partitionKey();

    this.eventStore = PersistenceExtensionHolder.getInstance().require()
        .<E>resolveStore(partitionKey).orElseThrow(() -> new PersistenceException(
            "Failed to resolve store for partition key '%s'".formatted(partitionKey.value())));

    this.events = new ArrayList<>();

    behaveAs(this::recoveringBehaviour);

    pipeFuture(this.eventStore.loadEvents(partitionKey).thenApply(events -> {
      //noinspection unchecked
      return (M) new RecoveryResultMessage<>(events);
    }).exceptionally(cause -> {
      //noinspection unchecked
      return (M) new RecoveryFailureMessage(partitionKey, cause);
    })).to(self());
  }

  @SuppressWarnings("unchecked")
  private void recoveringBehaviour(final @NotNull M message) {

    if (message instanceof RecoveryResultMessage<?> result) {

      this.events = new ArrayList<>(((RecoveryResultMessage<EventEnvelope<E>>) result).events());

      behaveAsDefault();
      afterRecovery();

    } else if (message instanceof RecoveryFailureMessage(PartitionKey id, Throwable cause)) {
      throw new RecoveryFailedException(id, cause);
    } else {
      reject(message);
    }
  }

  protected final void persist(final @NotNull E event) {
    persistMultiple(List.of(event));
  }

  protected final void persistMultiple(final @NotNull List<E> events) {

    final var partitionKey = partitionKey();

    try {
      final var addedEvents = this.eventStore.appendMultiple(partitionKey, events).join();
      this.events.addAll(addedEvents);
    } catch (final Exception cause) {
      throw new SaveFailedException(partitionKey, cause);
    }
  }

  /**
   * Defines the partition key that identifies the specific stream of events associated with this
   * actor.
   *
   * @return The partition key for this actor, which is used to load and persist events in the event
   * store.
   */
  protected abstract @NotNull PartitionKey partitionKey();

  /**
   * Defines a hook method that is called after the recovery process is complete and the actor has
   * switched to its default behavior. This method can be overridden by derived classes to perform
   * any necessary initialization or setup after the events have been successfully recovered.
   */
  protected void afterRecovery() {
    // no-op by default
  }

  /**
   * Returns an unmodifiable list of event envelopes representing the events that have been
   * recovered from the event store. This list is ordered by the events' ordering values, ensuring
   * that the sequence of events is maintained.
   *
   * @return An unmodifiable list of EventEnvelope objects representing the recovered events,
   * ordered by their ordering values.
   */
  protected final @NotNull List<EventEnvelope<E>> events() {
    return Collections.unmodifiableList(events);
  }
}
