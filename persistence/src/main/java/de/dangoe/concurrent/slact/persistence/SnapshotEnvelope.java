// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * A record representing a snapshot of the actor state at a specific point in time.
 *
 * @param ordering            the position of this snapshot in the snapshot sequence.
 * @param appliedUpToOrdering the highest event ordering already reflected in this snapshot.
 * @param timestamp           the instant this snapshot was persisted.
 * @param snapshot            the snapshot data representing the entity state.
 * @param <S>                 the snapshot data type.
 */
public record SnapshotEnvelope<S>(long ordering, long appliedUpToOrdering,
                                  @NotNull Instant timestamp, @NotNull S snapshot) {

}
