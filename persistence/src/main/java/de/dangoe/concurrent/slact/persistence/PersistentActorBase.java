// SPDX-License-Identifier: MIT OR Apache-2.0

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
 * Abstract base for persistent actors that recover from and append to an event store. Handles
 * startup recovery automatically, switches to normal behavior after recovery, and delegates
 * partition key and post-recovery hooks to subclasses.
 *
 * @param <M>  the message type.
 * @param <E>  the event type.
 * @param <R>  the recovery-data type.
 * @param <ST> the event-store type.
 */
// TODO Reduce visibility or hide via suitable module export rules
public abstract class PersistentActorBase<M, E, R extends RecoveryData<E>, ST extends EventStore> extends
    Actor<M> {

  /**
   * Carries the data needed to restore actor state after a restart.
   *
   * @param <E> the event type contained in the recovery data.
   */
  @FunctionalInterface
  public interface RecoveryData<E> {

    /**
     * Provides the ordered list of events to replay during recovery.
     *
     * @return the ordered list of events to replay during recovery.
     */
    @NotNull List<EventEnvelope<E>> events();
  }

  /**
   * Wraps the recovery payload delivered back to the actor after async recovery completes.
   *
   * @param <R>             the recovery-data type.
   * @param <E>             the event type.
   * @param recoveryPayload the recovery payload containing the events loaded from the event store.
   */
  protected record RecoveryResultMessage<R extends RecoveryData<E>, E>(@NotNull R recoveryPayload) {

  }

  private record RecoveryFailureMessage(@NotNull PartitionKey partitionKey,
                                        @NotNull Throwable cause) {

  }

  @NotNull
  private List<EventEnvelope<E>> events;

  /**
   * Creates a new persistent actor base with an empty event list.
   */
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
   * process using the provided recovery snapshot.
   *
   * @param recoveryPayload the recovery payload with events loaded from the event store.
   */
  protected abstract void recoverInternal(@NotNull R recoveryPayload);

  /**
   * Loads the recovery data for this actor from the backing store.
   *
   * @param partitionKey the partition key identifying this actor's event stream.
   * @return a future that completes with the recovery data.
   */
  protected abstract RichFuture<R> loadRecoveryData(@NotNull PartitionKey partitionKey);

  /**
   * Returns the event store used to load and persist events for this actor.
   *
   * @return the backing {@link EventStore}.
   */
  protected abstract @NotNull ST eventStore();

  /**
   * Persists a single event to the event store.
   *
   * @param event the event to append.
   */
  protected final void persist(final @NotNull E event) {
    persistMultiple(List.of(event));
  }

  /**
   * Persists multiple events atomically to the event store.
   *
   * @param events the list of events to persist.
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
   * Returns the partition key that identifies this actor's event stream.
   *
   * @return the partition key identifying this actor's event stream.
   */
  protected abstract @NotNull PartitionKey partitionKey();

  /**
   * Called after the actor has fully recovered and switched to its default behavior.
   */
  protected void afterRecovery() {
    // no-op by default
  }

  /**
   * Returns an unmodifiable snapshot of all events recovered from the store.
   *
   * @return an unmodifiable list of recovered event envelopes.
   */
  protected @NotNull List<EventEnvelope<E>> events() {
    return Collections.unmodifiableList(events);
  }

  /**
   * Returns the highest ordering value among the recovered events, or {@code -1} if there are
   * none.
   *
   * @return the maximum ordering value, or {@code -1} if no events exist.
   */
  protected long maxOrdering() {
    return this.events.stream().mapToLong(EventEnvelope::ordering).max().orElse(-1L);
  }
}
