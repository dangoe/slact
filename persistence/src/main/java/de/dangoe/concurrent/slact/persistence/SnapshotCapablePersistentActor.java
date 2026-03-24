package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.SnapshotCapablePersistentActor.SnapshotCapableRecoveryData;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An abstract base class for persistent actors that are capable of handling snapshots. It handles
 * the recovery process * during actor startup and allows derived classes to define their specific
 * behavior after recovery * is complete.
 *
 * @param <M> The type of messages that the actor will process.
 * @param <E> The type of domain events that the actor will persist and recover.
 * @param <S> The type of snapshot state that the actor will manage.
 */
public abstract class SnapshotCapablePersistentActor<M, E, S> extends
    PersistentActorBase<M, E, SnapshotCapableRecoveryData<E, S>, SnapshotCapableEventStore<E, S>> {

  public record SnapshotCapableRecoveryData<E, S>(@NotNull List<EventEnvelope<E>> events,
                                                  @Nullable SnapshotEnvelope<S> latestSnapshot) implements
      RecoveryData<E> {

  }

  @Nullable
  private S latestSnapshot;

  @Override
  protected final RichFuture<SnapshotCapableRecoveryData<E, S>> loadRecoveryData(
      final @NotNull PartitionKey partitionKey) {
    final var store = eventStore();
    return store.loadEvents(partitionKey).thenCompose(
        events -> store.loadLatestSnapshot(partitionKey).thenApply(
            latestSnapshot -> new SnapshotCapableRecoveryData<>(events,
                latestSnapshot.orElse(null))));
  }

  @Override
  protected final void recoverInternal(
      final @NotNull SnapshotCapableRecoveryData<E, S> recoveryPayload) {

    switch (recoveryPayload.latestSnapshot()) {
      case SnapshotEnvelope.SnapshotEventEnvelope<S> envelope ->
          this.latestSnapshot = envelope.snapshot();
      case SnapshotEnvelope.SnapshotMarkerEventEnvelope<S> ignored ->
          // A marker references a snapshot stored externally; the store implementation is
          // expected to resolve and return the actual snapshot data. If a marker reaches here,
          // snapshot state cannot be restored and recovery proceeds from events only.
          this.latestSnapshot = null;
      case null -> this.latestSnapshot = null;
    }
  }

  protected final @NotNull Optional<S> latestSnapshot() {
    return Optional.ofNullable(latestSnapshot);
  }

  @Override
  protected final @NotNull SnapshotCapableEventStore<E, S> eventStore() {
    return PersistenceExtensionHolder.getInstance().require()
        .<E, S>resolveSnapshotCapableStore(partitionKey()).orElseThrow(
            () -> new IllegalStateException(
                "Event store is not available for partition key '%s'".formatted(
                    partitionKey().value())));
  }
}
