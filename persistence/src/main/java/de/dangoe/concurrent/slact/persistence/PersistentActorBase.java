package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase.RecoveryData;
import de.dangoe.concurrent.slact.persistence.exception.RecoveryFailedException;
import de.dangoe.concurrent.slact.persistence.exception.SaveFailedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * An abstract base class for persistent actors that provides common functionality for managing an
 * event store and handling event persistence. This class is designed to be extended by concrete
 * actor implementations that require persistence capabilities. It defines the structure for
 * recovering events from an event store and persisting new events, while allowing derived classes
 * to specify their own partition key and post-recovery behavior.
 *
 * @param <M>  The type of messages that the actor will process.
 * @param <E>  The type of domain events that the actor will persist and recover.
 * @param <ST> The type of event store that the actor will use for persisting and recovering events.
 *             This type must extend the EventStore interface.
 */
// TODO Reduce visibility or hide via suitable module export rules
public abstract class PersistentActorBase<M, E, R extends RecoveryData<E>, ST extends EventStore> extends
    Actor<M> {

  @FunctionalInterface
  public interface RecoveryData<E> {

    @NotNull List<EventEnvelope<E>> events();
  }

  protected record RecoveryResultMessage<R extends RecoveryData<E>, E>(@NotNull R recoveryPayload) {

  }

  private record RecoveryFailureMessage(@NotNull PartitionKey partitionKey,
                                        @NotNull Throwable cause) {

  }

  @NotNull
  private List<EventEnvelope<E>> events;

  public PersistentActorBase() {
    this.events = new ArrayList<>();
  }

  @Override
  public final void onStart() {

    final var partitionKey = partitionKey();

    this.events = new ArrayList<>();

    behaveAs(this::recoveringBehaviour);

    pipeFuture(loadRecoveryData(partitionKey).thenApply(recoveryData -> {
      //noinspection unchecked
      return (M) new RecoveryResultMessage<>(recoveryData);
    }).exceptionally(cause -> {
      //noinspection unchecked
      return (M) new RecoveryFailureMessage(partitionKey, cause);
    })).to(self());
  }

  @SuppressWarnings("unchecked")
  private void recoveringBehaviour(final @NotNull M message) {

    if (message instanceof RecoveryResultMessage<?, ?>(Object rawRecoveryPayload)) {

      final var recoveryPayload = (R) rawRecoveryPayload;

      this.events = new ArrayList<>(recoveryPayload.events());

      recoverInternal(recoveryPayload);

      behaveAsDefault();
      afterRecovery();

    } else if (message instanceof RecoveryFailureMessage(PartitionKey id, Throwable cause)) {
      throw new RecoveryFailedException(id, cause);
    } else {
      reject(message);
    }
  }

  /**
   * Defines an abstract method that must be implemented by derived classes to perform the recovery
   * process using the provided recovery snapshot. This method is called after the recovery data has
   * been successfully loaded and the events have been stored in the internal list.
   *
   * @param recoveryPayload The recovery snapshot containing the events that have been recovered
   *                        from the event store.
   */
  protected abstract void recoverInternal(@NotNull R recoveryPayload);

  /**
   * Defines an abstract method that must be implemented by derived classes to load the recovery
   * data for the actor based on the provided partition key.
   *
   * @param partitionKey The partition key for which to load the recovery data.
   * @return A RichFuture that, when completed, will contain the recovery data of type R, which
   * includes the list of events to be recovered for the actor.
   */
  protected abstract RichFuture<R> loadRecoveryData(@NotNull PartitionKey partitionKey);

  protected abstract @NotNull ST eventStore();

  /**
   * Persists a single event to the event store. This method is a convenience wrapper around the
   * persistMultiple method, allowing derived classes to persist individual events without needing
   * to create a list.
   *
   * @param event The event to be persisted. This event will be appended to the event store and
   *              added to the internal list of events if the operation is successful.
   */
  protected final void persist(final @NotNull E event) {
    persistMultiple(List.of(event));
  }

  /**
   * Persists multiple events to the event store in a single operation. This method appends the
   * provided list of events to the event store using the partition key defined by the derived
   * class. If the persistence operation is successful, the newly added events are appended to the
   * internal list of events. If any exception occurs during the persistence process, a
   * SaveFailedException is thrown with details about the partition key and the cause of the
   * failure.
   *
   * @param events The list of events to be persisted. These events will be appended to the event
   *               store and added to the internal list of events if the operation is successful.
   */
  protected void persistMultiple(final @NotNull List<E> events) {

    final var partitionKey = partitionKey();

    try {
      final var addedEvents = eventStore().appendMultiple(partitionKey, maxOrdering(), events)
          .join();
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
  protected @NotNull List<EventEnvelope<E>> events() {
    return Collections.unmodifiableList(events);
  }

  /**
   * Calculates the maximum ordering value among the currently stored events. This method is used to
   * determine the correct ordering for new events being persisted, ensuring that they are appended
   * after the existing events in the event store. If there are no events currently stored, this
   * method returns -1.
   *
   * @return The maximum ordering value among the currently stored events, or -1 if there are no
   * events.
   */
  protected long maxOrdering() {
    return this.events.stream().mapToLong(EventEnvelope::ordering).max().orElse(-1L);
  }
}
