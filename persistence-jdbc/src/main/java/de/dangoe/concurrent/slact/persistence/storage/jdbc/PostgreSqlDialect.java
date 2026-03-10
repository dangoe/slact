package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * PostgreSQL dialect that uses {@code INSERT ... RETURNING} to retrieve generated ordering and
 * timestamp values without a separate round-trip.
 */
public class PostgreSqlDialect implements JdbcDialect {

  @Override
  public <E> @NotNull List<EventEnvelope<E>> insertEvents(
      final @NotNull Connection connection,
      final @NotNull PartitionKey partitionKey,
      final @NotNull List<E> events,
      final @NotNull Function<E, byte[]> serializer) throws SQLException {

    final var inserted = new ArrayList<EventEnvelope<E>>();

    for (final var event : events) {
      try (final var statement = connection.prepareStatement(
          "INSERT INTO events (partition_key, timestamp, payload) VALUES (?, ?, ?) RETURNING ordering, timestamp")) {

        statement.setString(1, partitionKey.value());
        statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
        statement.setBytes(3, serializer.apply(event));

        try (final var resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            final var ordering = resultSet.getLong("ordering");
            final var timestamp = resultSet.getTimestamp("timestamp").toInstant();
            inserted.add(new EventEnvelope<>(ordering, timestamp, event));
          }
        }
      }
    }

    return inserted;
  }
}
