package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.SnapshotEnvelope;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SPI for translating event-store operations into dialect-specific SQL statements.
 */
public interface JdbcDialect {

  /**
   * Loads all events for the given partition key and an ordering greater or equal to the given
   * <code>fromOrdering</code> value, ordered by their natural ordering (e.g. insertion order). The
   * deserializer is used to convert the binary snapshot back into event objects.
   *
   * @param connection   an active JDBC connection.
   * @param partitionKey the partition key to load events for.
   * @param fromOrdering the minimum ordering; only events with ordering ≥ this value are included.
   * @param <E>          the event type.
   * @return a list of event envelopes, in natural ordering.
   * @throws SQLException if a database error occurs.
   */
  <E> @NotNull List<EventEnvelope<E>> loadEvents(@NotNull Connection connection,
      @NotNull PartitionKey partitionKey, long fromOrdering) throws SQLException;

  /**
   * Inserts the given events for the partition and returns their persisted envelopes, including
   * database-generated values such as ordering and timestamp.
   *
   * @param connection      an active JDBC connection.
   * @param partitionKey    the partition key to insert events under.
   * @param lastMaxOrdering the last known maximum ordering, used for optimistic concurrency
   *                        detection.
   * @param events          the events to insert.
   * @param <E>             the event type.
   * @return the persisted envelopes for the inserted events, in insertion order.
   * @throws SQLException             if a database error occurs.
   * @throws ConcurrentWriteException if a concurrent write is detected.
   */
  <E> @NotNull List<EventEnvelope<E>> insertEvents(@NotNull Connection connection,
      @NotNull PartitionKey partitionKey, final long lastMaxOrdering, @NotNull List<E> events)
      throws SQLException, ConcurrentWriteException;

  /**
   * Loads the latest snapshot for the given partition key, if available.
   *
   * @param connection   an active JDBC connection.
   * @param partitionKey the partition key to load the snapshot for.
   * @param <S>          the snapshot type.
   * @return an optional containing the latest snapshot envelope, or empty if none exists.
   * @throws SQLException if a database error occurs.
   */
  <S> @NotNull Optional<SnapshotEnvelope<S>> loadLatestSnapshot(@NotNull Connection connection,
      @NotNull PartitionKey partitionKey) throws SQLException;

  /**
   * Inserts a new snapshot for the given partition key. The snapshot will be associated with the
   * given ordering value, which indicates up to which events have been applied to the snapshot
   * state.
   *
   * @param connection           an active JDBC connection.
   * @param partitionKey         the partition key to insert the snapshot for.
   * @param lastSnapshotOrdering the ordering of the previous snapshot, or {@code null} if none
   *                             exists.
   * @param appliedUpToOrdering  the highest event ordering already reflected in this snapshot.
   * @param snapshot             the snapshot data representing the entity state.
   * @param <S>                  the snapshot type.
   * @return the persisted snapshot envelope.
   * @throws SQLException if a database error occurs.
   */
  <S> @NotNull SnapshotEnvelope<S> insertSnapshot(@NotNull Connection connection,
      @NotNull PartitionKey partitionKey, @Nullable Long lastSnapshotOrdering,
      long appliedUpToOrdering, @NotNull S snapshot) throws SQLException, ConcurrentWriteException;

  /**
   * Returns a {@link JdbcExceptionTranslator} that maps vendor-specific {@link SQLException}
   * instances to the appropriate {@link PersistenceException} subtype. The default implementation
   * always returns a generic {@link PersistenceException}.
   *
   * @return a translator for this dialect.
   */
  default @NotNull JdbcExceptionTranslator exceptionTranslator() {
    return (partitionKey, cause) -> new PersistenceException(
        "Failed to execute database operation for partition key '%s'.".formatted(partitionKey),
        cause);
  }
}
