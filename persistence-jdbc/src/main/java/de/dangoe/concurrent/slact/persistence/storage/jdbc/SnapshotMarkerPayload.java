package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import java.io.Serial;
import java.io.Serializable;

/**
 * Internal snapshot written to the {@code events} table when a snapshot is saved. Occupies the
 * same ordering slot as the corresponding snapshot row, preventing any other writer from claiming
 * that ordering concurrently.
 *
 * @param snapshotOrdering    The ordering of both this marker and the snapshot row.
 * @param appliedUpToOrdering The ordering of the last domain event compacted into the snapshot.
 */
record SnapshotMarkerPayload(long snapshotOrdering, long appliedUpToOrdering)
    implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;
}
