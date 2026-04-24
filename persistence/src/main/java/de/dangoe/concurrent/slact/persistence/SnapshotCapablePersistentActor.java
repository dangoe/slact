// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.SnapshotCapablePersistentActor.SnapshotCapableRecoveryData;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract base for persistent actors that also support snapshotting to speed up recovery. Extends
 * {@link PersistentActorBase} by restoring the latest snapshot first, then replaying only the
 * events recorded after that snapshot.
 *
 * <p>Example:
 * <pre>{@code
 * public class CartActor extends SnapshotCapablePersistentActor<CartCommand, CartEvent, CartState> {
 *
 *     @Override protected PartitionKey partitionKey() { return PartitionKey.of("cart-99"); }
 *
 *     @Override
 *     protected SnapshottingStrategy<CartEvent, CartState> snapshottingStrategy() {
 *         return SnapshottingStrategy.afterEvery(50, this::buildState);
 *     }
 *
 *     @Override
 *     public void onMessage(CartCommand cmd) { persist(new CartEvent(cmd)); }
 * }
 * }</pre>
 *
 * @param <M> the message type.
 * @param <E> the event type.
 * @param <S> the snapshot state type.
 */
public abstract class SnapshotCapablePersistentActor<M, E, S> extends
    PersistentActorBase<M, E, SnapshotCapableRecoveryData<E, S>, SnapshotCapableEventStore> {

  /**
   * Creates a new snapshot-capable persistent actor.
   */
  protected SnapshotCapablePersistentActor() {
    super();
  }

  /**
   * Recovery data combining replayed events with the latest snapshot envelope.
   *
   * @param events                 events replayed after the snapshot was applied.
   * @param latestSnapshotEnvelope the most recent snapshot envelope, or {@code null} if none
   *                               exists.
   * @param <E>                    the event type.
   * @param <S>                    the snapshot state type.
   */
  public record SnapshotCapableRecoveryData<E, S>(@NotNull List<EventEnvelope<E>> events,
                                                  @Nullable SnapshotEnvelope<S> latestSnapshotEnvelope) implements
      RecoveryData<E> {

  }

  @Nullable
  private SnapshotEnvelope<S> latestSnapshot;

  @Override
  protected final RichFuture<SnapshotCapableRecoveryData<E, S>> loadRecoveryData(
      final @NotNull PartitionKey partitionKey) {

    final var store = eventStore();

    return store.loadLatestSnapshot(partitionKey, snapshotType()).thenCompose(
        maybeLatestSnapshotEnvelope -> maybeLatestSnapshotEnvelope.map(
                latestSnapshotEnvelope -> store.<E>loadEvents(partitionKey,
                    latestSnapshotEnvelope.appliedUpToOrdering() + 1).thenApply(
                    events -> new SnapshotCapableRecoveryData<>(events, latestSnapshotEnvelope)))
            .orElseGet(() -> store.<E>loadEvents(partitionKey)
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
   * Returns the class of the snapshot type {@code S}, used when loading the latest snapshot for
   * deserialization.
   *
   * @return the class representing the snapshot type {@code S}.
   */
  protected abstract @NotNull Class<S> snapshotType();

  /**
   * Returns the strategy that determines when and how to take a snapshot.
   *
   * @return the snapshotting strategy for this actor.
   */
  protected abstract @NotNull SnapshottingStrategy<E, S> snapshottingStrategy();

  /**
   * Returns the latest snapshot state restored during recovery, if any.
   *
   * @return the latest snapshot, or an empty optional if no snapshot has been recovered.
   */
  protected final @NotNull Optional<S> latestSnapshot() {
    return Optional.ofNullable(latestSnapshot).map(SnapshotEnvelope::snapshot);
  }

  @Override
  protected final @NotNull SnapshotCapableEventStore eventStore() {
    return PersistenceExtensionHolder.getInstance().require()
        .resolveSnapshotCapableStore(partitionKey()).orElseThrow(() -> new IllegalStateException(
            "Event store is not available for partition key '%s'".formatted(partitionKey().raw())));
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
