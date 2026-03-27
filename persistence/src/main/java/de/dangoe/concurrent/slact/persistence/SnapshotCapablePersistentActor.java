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
    PersistentActorBase<M, E, SnapshotCapableRecoveryData<E, S>, SnapshotCapableEventStore> {

  public record SnapshotCapableRecoveryData<E, S>(@NotNull List<EventEnvelope<E>> events,
                                                  @Nullable SnapshotEnvelope<S> latestSnapshotEnvelope) implements
      RecoveryData<E> {

  }

  @Nullable
  private SnapshotEnvelope<S> latestSnapshot;

  @Override
  protected final RichFuture<SnapshotCapableRecoveryData<E, S>> loadRecoveryData(
      final @NotNull PartitionKey<E> partitionKey) {

    final var store = eventStore();

    return store.loadLatestSnapshot(partitionKey, snapshotType()).thenCompose(
        maybeLatestSnapshotEnvelope -> maybeLatestSnapshotEnvelope.map(
                latestSnapshotEnvelope -> store.loadEvents(partitionKey,
                    latestSnapshotEnvelope.appliedUpToOrdering() + 1).thenApply(
                    events -> new SnapshotCapableRecoveryData<>(events, latestSnapshotEnvelope)))
            .orElseGet(() -> store.loadEvents(partitionKey)
                .thenApply(events -> new SnapshotCapableRecoveryData<>(events, null))));
  }

  @Override
  protected final void recoverInternal(
      final @NotNull SnapshotCapableRecoveryData<E, S> recoveryPayload) {

    if (recoveryPayload.latestSnapshotEnvelope() != null) {
      this.latestSnapshot = recoveryPayload.latestSnapshotEnvelope();
    }
  }

  @Override
  protected final @NotNull List<EventEnvelope<E>> events() {
    return super.events().stream().filter(eventEnvelope -> latestSnapshot == null
        || eventEnvelope.ordering() > latestSnapshot.appliedUpToOrdering()).toList();
  }

  /**
   * Returns the class of the snapshot type. This is used when loading the latest snapshot from the
   * event store so that the snapshot data can be deserialized into the correct type.
   *
   * @return The class of the snapshot type {@code S}.
   */
  protected abstract @NotNull Class<S> snapshotType();

    protected abstract @NotNull SnapshottingStrategy<E, S> snapshottingStrategy();

  protected final @NotNull Optional<S> latestSnapshot() {
    return Optional.ofNullable(latestSnapshot).map(SnapshotEnvelope::snapshot);
  }

  @Override
  protected final @NotNull SnapshotCapableEventStore eventStore() {
    return PersistenceExtensionHolder.getInstance().require()
        .resolveSnapshotCapableStore(partitionKey()).orElseThrow(
            () -> new IllegalStateException(
                "Event store is not available for partition key '%s'".formatted(
                    partitionKey().value())));
  }

  @Override
  protected final void persistMultiple(final @NotNull List<E> events) {

    super.persistMultiple(events);

    final var currentEvents = events();

    snapshottingStrategy().tryCreateSnapshot(currentEvents, latestSnapshot).ifPresent(
        createdSnapshot -> this.latestSnapshot = eventStore().saveSnapshot(partitionKey(),
            Optional.ofNullable(latestSnapshot).map(SnapshotEnvelope::ordering).orElse(null),
            createdSnapshot.appliedUpToOrdering(), createdSnapshot.snapshot()).join());
  }
}
