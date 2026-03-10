package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Abstracts database-specific SQL for inserting events and retrieving their generated metadata.
 * The SELECT path is standard SQL and does not require a dialect.
 */
public interface JdbcDialect {

  /**
   * Inserts the given events for the partition and returns their persisted envelopes, including
   * database-generated values such as ordering and timestamp.
   *
   * @param connection    An active JDBC connection.
   * @param partitionKey  The partition key to insert events under.
   * @param events        The events to insert.
   * @param serializer    Converts an event to its binary representation for storage.
   * @param <E>           The event type.
   * @return The persisted envelopes for the inserted events, in insertion order.
   * @throws SQLException If a database error occurs.
   */
  <E> @NotNull List<EventEnvelope<E>> insertEvents(
      @NotNull Connection connection,
      @NotNull PartitionKey partitionKey,
      @NotNull List<E> events,
      @NotNull Function<E, byte[]> serializer) throws SQLException;
}
