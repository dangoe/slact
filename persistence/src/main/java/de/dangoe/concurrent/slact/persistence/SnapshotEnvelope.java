package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.SnapshotEnvelope.SnapshotEventEnvelope;
import de.dangoe.concurrent.slact.persistence.SnapshotEnvelope.SnapshotMarkerEventEnvelope;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an envelope for snapshot-related events in the persistence layer.
 *
 * @param <S> The type of the snapshot payload contained in the envelope.
 */
public sealed interface SnapshotEnvelope<S> extends EventLogEntryLike permits SnapshotEventEnvelope,
    SnapshotMarkerEventEnvelope {

  /**
   * Represents a snapshot event envelope that contains the actual snapshot data. This record is
   * used to encapsulate a snapshot event, including its ordering, timestamp, and the snapshot
   * payload itself.
   *
   * @param ordering  The ordering of the snapshot event within its partition.
   * @param timestamp The time the snapshot event was persisted.
   * @param snapshot  The actual snapshot payload of type <code>S</code> that is being stored in
   *                  this envelope.
   * @param <S>       The type of the snapshot payload contained in this envelope.
   */
  record SnapshotEventEnvelope<S>(long ordering, @NotNull Instant timestamp,
                                  @NotNull S snapshot) implements SnapshotEnvelope<S> {

  }

  /**
   * Represents a snapshot marker event envelope that serves as a marker for the presence of a
   * snapshot without containing the actual snapshot data. This record is used to indicate that a
   * snapshot exists at a certain ordering and timestamp, but does not include the snapshot payload
   * itself.
   *
   * @param ordering              The ordering of the snapshot marker event within its partition.
   * @param timestamp             The time the snapshot marker event was persisted.
   * @param snapshotEntryOrdering The ordering of the snapshot entry that this marker refers to.
   *                              This indicates the position of the snapshot in the event stream,
   *                              allowing for efficient retrieval of the snapshot without needing
   *                              to load the actual snapshot data.
   * @param <S>                   The type of the snapshot payload that this marker event refers to,
   *                              even though it does not contain the actual snapshot data.
   */
  record SnapshotMarkerEventEnvelope<S>(long ordering, @NotNull Instant timestamp,
                                        long snapshotEntryOrdering) implements SnapshotEnvelope<S> {

  }
}
