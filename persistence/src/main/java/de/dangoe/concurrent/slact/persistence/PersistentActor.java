package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase.RecoveryData;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A base class for actors that require persistence capabilities. This class provides mechanisms for
 * recovering state from an event store and persisting new events. It handles the recovery process
 * during actor startup and allows derived classes to define their specific behavior after recovery
 * is complete.
 *
 * @param <M> The type of messages that the actor will process.
 * @param <E> The type of domain events that the actor will persist and recover.
 */
public abstract class PersistentActor<M, E> extends
    PersistentActorBase<M, E, RecoveryData<E>, EventStore<E>> {

  @Override
  protected final RichFuture<RecoveryData<E>> loadRecoveryData(@NotNull PartitionKey partitionKey) {
    return eventStore().loadEvents(partitionKey).thenApply(it -> () -> it);
  }

  @Override
  protected final void recoverInternal(
      final @NotNull PersistentActorBase.RecoveryData<E> recoveryPayload) {

    // Nothing to do here, as the events are already loaded and can be accessed via the recovery snapshot.
  }

  @Override
  protected final @NotNull List<EventEnvelope<E>> events() {
    return super.events();
  }

  @Override
  protected final @NotNull EventStore<E> eventStore() {
    return PersistenceExtensionHolder.getInstance().require().<E>resolveStore(partitionKey())
        .orElseThrow(() -> new IllegalStateException(
            "Event store is not available for partition key '%s'".formatted(
                partitionKey().value())));
  }

  @Override
  protected final void persistMultiple(final @NotNull List<E> events) {
    super.persistMultiple(events);
  }
}
