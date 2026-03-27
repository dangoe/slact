package de.dangoe.concurrent.slact.persistence;

import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a strategy for determining when to create snapshots based on the events that have
 * occurred since the last snapshot. This interface allows for flexible snapshotting strategies that
 * can be tailored to the specific needs of the application, such as creating snapshots after a
 * certain number of events have been processed or based on specific conditions in the event
 * stream.
 *
 * @param <E> The type of domain events that the snapshotting strategy will evaluate to determine
 *            when to create a snapshot.
 * @param <S> The type of snapshot state that the snapshotting strategy will manage. This type
 *            represents the state of the entity at a specific point in time and can be used for
 *            efficient recovery without needing to replay all events from the beginning of the
 *            event stream.
 */
public abstract class SnapshottingStrategy<E, S> {

  /**
   * Evaluates the given list of events and the latest snapshot to determine whether a new snapshot
   * should be created.
   *
   * @param events         The list of events.
   * @param latestSnapshot The latest snapshot, if available.
   * @return An optional containing the new snapshot state if a snapshot should be created based on
   * the provided events and the latest snapshot, or an empty optional if no snapshot should be
   * created at this time.
   */
  public @NotNull Optional<S> tryCreateSnapshot(@NotNull List<EventEnvelope<E>> events,
      @Nullable SnapshotEnvelope<S> latestSnapshot) {

    if (!events.isEmpty()) {
      return tryCreateSnapshotInternal(events, latestSnapshot);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Evaluates the given list of events and the latest snapshot to determine whether a new snapshot
   * should be created. This method is only called if the list of events is not empty.
   *
   * @param events         The non-empty list of events.
   * @param latestSnapshot The latest snapshot, if available.
   * @return An optional containing the new snapshot state if a snapshot should be created based on
   * * the provided events and the latest snapshot, or an empty optional if no snapshot should be *
   * created at this time.
   */
  protected abstract @NotNull Optional<S> tryCreateSnapshotInternal(
      @NotNull List<EventEnvelope<E>> events, @Nullable SnapshotEnvelope<S> latestSnapshot);
}
