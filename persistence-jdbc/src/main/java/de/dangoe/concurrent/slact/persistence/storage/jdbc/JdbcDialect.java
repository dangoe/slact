package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Abstracts database-specific SQL for inserting events and retrieving their generated metadata. The
 * SELECT path is standard SQL and does not require a dialect.
 */
public interface JdbcDialect {

  /**
   * Loads all events for the given partition key, ordered by their natural ordering (e.g. insertion
   * order). The deserializer is used to convert the binary payload back into event objects.
   *
   * @param connection   An active JDBC connection.
   * @param partitionKey The partition key to load events for.
   * @param <E>          The event type.
   * @return A list of event envelopes containing the loaded events and their associated metadata,
   * ordered by their natural ordering.
   * @throws SQLException Thrown, if a database error occurs while loading events.
   */
  <E> @NotNull List<EventEnvelope<E>> loadEvents(@NotNull Connection connection,
      @NotNull PartitionKey partitionKey) throws SQLException;

  /**
   * Inserts the given events for the partition and returns their persisted envelopes, including
   * database-generated values such as ordering and timestamp.
   *
   * @param connection      An active JDBC connection.
   * @param partitionKey    The partition key to insert events under.
   * @param lastMaxOrdering The last known maximum ordering value for the events in the partition.
   *                        This is used to detect any potential concurrency issues.
   * @param events          The events to insert.
   * @param <E>             The event type.
   * @return The persisted envelopes for the inserted events, in insertion order.
   * @throws SQLException             Thrown, if a database error occurs.
   * @throws ConcurrentWriteException Thrown, if a concurrency conflict is detected, i.e., if the
   *                                  last known maximum ordering value does not match the current
   *                                  maximum ordering in the event store.
   */
  <E> @NotNull List<EventEnvelope<E>> insertEvents(@NotNull Connection connection,
      @NotNull PartitionKey partitionKey, final long lastMaxOrdering, @NotNull List<E> events)
      throws SQLException, ConcurrentWriteException;
}
