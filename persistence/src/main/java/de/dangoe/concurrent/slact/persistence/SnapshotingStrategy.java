package de.dangoe.concurrent.slact.persistence;

import java.util.List;
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
public interface SnapshotingStrategy<E, S> {

  /**
   * Determines whether a snapshot should be created based on the provided list of all events that
   * have occurred since the last snapshot and the latest snapshot envelope, if available.
   *
   * @param events         A list of events that have occurred since the last snapshot. This list
   *                       includes all events that have been processed by the actor since the last
   *                       snapshot was taken, and it is used to evaluate whether the conditions for
   *                       creating a new snapshot are met.
   * @param latestSnapshot The latest snapshot envelope, if available.
   * @return <code>true</code>, if a snapshot should be created based on the provided events and the
   * latest snapshot; false otherwise.
   */
  boolean shouldSnapshot(@NotNull List<EventEnvelope<E>> events,
      @Nullable SnapshotEnvelope<S> latestSnapshot);

  /**
   * A static factory method that creates a snapshotting strategy which triggers a snapshot after
   * every N events have been processed since the last snapshot.
   *
   * @param n   The number of events that must be processed since the last snapshot before a new
   *            snapshot should be created.
   * @param <E> The type of domain events that the snapshotting strategy will evaluate to determine
   *            when to create a snapshot.
   * @param <S> The type of snapshot state that the snapshotting strategy will manage.
   * @return A {@link SnapshotingStrategy} that triggers a snapshot after every N events have been
   * processed since the last snapshot.
   */
  static <E, S> SnapshotingStrategy<E, S> everyN(int n) {
    return (events, latest) -> {
      final long baseline = latest != null ? latest.ordering() : -1L;
      final long eventsSinceSnapshot = events.stream().filter(e -> e.ordering() > baseline).count();
      return eventsSinceSnapshot >= n;
    };
  }
}
