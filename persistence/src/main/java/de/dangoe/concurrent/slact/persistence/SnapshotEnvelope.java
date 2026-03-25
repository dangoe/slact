package de.dangoe.concurrent.slact.persistence;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * A record representing a snapshot of the snapshot state at a specific point in time. It contains
 * the ordering of the snapshot, the ordering up to which events have been applied, a timestamp
 * indicating when the snapshot was taken, and the actual snapshot data.
 *
 * @param ordering            The ordering value of the snapshot, which indicates its position in
 *                            the event stream. This value is used to determine the sequence of
 *                            snapshots and their relationship to the events that have been applied
 *                            up to that point.
 * @param appliedUpToOrdering The ordering value up to which events have been applied to the
 *                            snapshot state. This indicates the point in the event stream up to
 *                            which the snapshot reflects the state of the entity.
 * @param timestamp           The timestamp indicating when the snapshot was taken. This provides
 *                            temporal context for the snapshot and can be used for auditing or
 *                            debugging purposes.
 * @param snapshot            The actual snapshot data representing the state of the entity at the
 *                            time the snapshot was taken. This is the core content of the snapshot
 *                            and can be used to restore the state of the entity without needing to
 *                            replay all events from the beginning of the event stream.
 * @param <S>                 The type of the snapshot data contained in the snapshot. This allows
 *                            for flexibility in defining the structure of the snapshot based on the
 *                            specific requirements of the application or domain model.
 */
public record SnapshotEnvelope<S>(long ordering, long appliedUpToOrdering,
                                  @NotNull Instant timestamp, @NotNull S snapshot) {

}
