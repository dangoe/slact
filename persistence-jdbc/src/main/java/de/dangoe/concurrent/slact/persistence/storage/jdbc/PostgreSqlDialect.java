package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * PostgreSQL dialect implementation of the JdbcDialect interface. This class provides the necessary
 * SQL statements and handling for inserting events and retrieving their metadata in a PostgreSQL
 * database.
 */
public class PostgreSqlDialect implements JdbcDialect {

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull <E> List<EventEnvelope<E>> loadEvents(final @NotNull Connection connection,
      final @NotNull PartitionKey partitionKey) throws SQLException {

    try (final var statement = connection.prepareStatement(
        "SELECT ordering, timestamp, payload FROM events WHERE partition_key = ? ORDER BY ordering ASC")) {

      statement.setString(1, partitionKey.value());

      try (final var resultSet = statement.executeQuery()) {

        final var events = new ArrayList<EventEnvelope<E>>();

        while (resultSet.next()) {

          final var ordering = resultSet.getLong("ordering");
          final var timestamp = resultSet.getTimestamp("timestamp").toInstant();
          final var payload = resultSet.getBytes("payload");
          final var event = (E) deserialize(payload);

          events.add(new EventEnvelope<>(ordering, timestamp, event));
        }

        return events;
      }
    }
  }

  @Override
  public <E> @NotNull List<EventEnvelope<E>> insertEvents(final @NotNull Connection connection,
      final @NotNull PartitionKey partitionKey, long lastMaxOrdering, final @NotNull List<E> events)
      throws SQLException, ConcurrentWriteException {

    final var inserted = new ArrayList<EventEnvelope<E>>();

    var ordering = lastMaxOrdering + 1;

    for (final var event : events) {
      try (final var statement = connection.prepareStatement(
          "INSERT INTO events (partition_key, ordering, timestamp, payload) VALUES (?, ?, ?, ?) RETURNING timestamp")) {

        statement.setString(1, partitionKey.value());
        statement.setLong(2, ordering);
        statement.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
        statement.setBytes(4, serialize(event));

        try (final var resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            final var timestamp = resultSet.getTimestamp("timestamp").toInstant();
            inserted.add(new EventEnvelope<>(ordering, timestamp, event));
          }
        }

        ordering++;
      }
    }

    return inserted;
  }


  private <E> byte[] serialize(final @NotNull E event) {
    try (final var byteArrayOutputStream = new ByteArrayOutputStream(); final var objectOutputStream = new ObjectOutputStream(
        byteArrayOutputStream)) {
      objectOutputStream.writeObject(event);
      return byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      throw new PersistenceException("Failed to serialize event", e);
    }
  }

  @SuppressWarnings("unchecked")
  private <E> @NotNull E deserialize(final byte[] bytes) {
    try (final var byteArrayInputStream = new ByteArrayInputStream(
        bytes); final var objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
      return (E) objectInputStream.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new PersistenceException("Failed to deserialize event", e);
    }
  }
}
