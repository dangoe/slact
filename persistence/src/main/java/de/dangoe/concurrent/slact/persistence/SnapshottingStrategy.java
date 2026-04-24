// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Strategy that decides when to create a snapshot based on the current event list and the latest
 * snapshot. Implement to control snapshot frequency.
 *
 * @param <E> the type of domain events evaluated by this strategy.
 * @param <S> the type of snapshot state produced by this strategy.
 */
@FunctionalInterface
public interface SnapshottingStrategy<E, S> {

  /**
   * Holds the snapshot state and the event ordering up to which it was applied.
   *
   * @param <S>                 the type of snapshot state.
   * @param appliedUpToOrdering the event ordering up to which the snapshot was applied.
   * @param snapshot            the snapshot state.
   */
  record CreatedSnapshot<S>(long appliedUpToOrdering, @NotNull S snapshot) {

  }

  /**
   * Evaluates the given list of events and the latest snapshot to determine whether a new snapshot
   * should be created.
   *
   * @param events         the list of events.
   * @param latestSnapshot the latest snapshot, if available.
   * @return an optional containing a new snapshot if the threshold is met, or empty if no snapshot
   * should be created.
   */
  @NotNull Optional<CreatedSnapshot<S>> tryCreateSnapshot(@NotNull List<EventEnvelope<E>> events,
      @Nullable SnapshotEnvelope<S> latestSnapshot);
}
