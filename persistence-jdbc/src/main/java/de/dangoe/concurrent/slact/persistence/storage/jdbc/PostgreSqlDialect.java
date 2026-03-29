package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.SnapshotEnvelope;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import de.dangoe.concurrent.slact.persistence.exception.SaveFailedException;
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
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO Allow to provide suitable serde mechanisms instead of relying on Java's built-in serialization, which is not recommended for production use due to performance and security concerns.
public class PostgreSqlDialect implements JdbcDialect {

  private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

  @Override
  public @NotNull <E> List<EventEnvelope<E>> loadEvents(final @NotNull Connection connection,
      final @NotNull PartitionKey partitionKey, final long fromOrdering) throws SQLException {

    try (final var statement = connection.prepareStatement(
        "SELECT ordering, timestamp, payload FROM events WHERE partition_key = ? AND ordering >= ? ORDER BY ordering ASC")) {

      statement.setString(1, partitionKey.raw());
      statement.setLong(2, fromOrdering);

      try (final var resultSet = statement.executeQuery()) {

        final var events = new ArrayList<EventEnvelope<E>>();

        while (resultSet.next()) {

          final var ordering = resultSet.getLong("ordering");
          final var timestamp = resultSet.getTimestamp("timestamp").toInstant();
          final var payload = resultSet.getBytes("payload");
          final var event = this.<E>deserialize(payload);

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

        statement.setString(1, partitionKey.raw());
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
      } catch (final SQLException e) {
        if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
          throw new ConcurrentWriteException(partitionKey);
        }
        throw e;
      }
    }

    return inserted;
  }

  @Override
  public @NotNull <S> Optional<SnapshotEnvelope<S>> loadLatestSnapshot(
      final @NotNull Connection connection, final @NotNull PartitionKey partitionKey)
      throws SQLException {

    try (final var statement = connection.prepareStatement(
        "SELECT ordering, event_ordering, timestamp, payload FROM snapshots WHERE partition_key = ? ORDER BY ordering DESC LIMIT 1")) {

      statement.setString(1, partitionKey.raw());

      try (final var resultSet = statement.executeQuery()) {

        if (resultSet.next()) {

          final var ordering = resultSet.getLong("ordering");
          final var eventOrdering = resultSet.getLong("event_ordering");
          final var timestamp = resultSet.getTimestamp("timestamp").toInstant();
          final var payload = resultSet.getBytes("payload");
          final var snapshot = this.<S>deserialize(payload);

          return Optional.of(new SnapshotEnvelope<>(ordering, eventOrdering, timestamp, snapshot));
        } else {
          return Optional.empty();
        }
      }
    }
  }

  @Override
  public <S> SnapshotEnvelope<S> insertSnapshot(@NotNull Connection connection,
      final @NotNull PartitionKey partitionKey, final @Nullable Long lastSnapshotOrdering,
      final long appliedUpToOrdering, final @NotNull S snapshot)
      throws SQLException, ConcurrentWriteException {

    final var ordering = (lastSnapshotOrdering != null ? lastSnapshotOrdering : 0) + 1;

    try (final var statement = connection.prepareStatement(
        "INSERT INTO snapshots (partition_key, ordering, event_ordering, timestamp, payload) VALUES (?,?, ?, ?, ?) RETURNING timestamp")) {

      statement.setString(1, partitionKey.raw());
      statement.setLong(2, ordering);
      statement.setLong(3, appliedUpToOrdering);
      statement.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
      statement.setBytes(5, serialize(snapshot));

      try (final var resultSet = statement.executeQuery()) {

        if (resultSet.next()) {

          final var timestamp = resultSet.getTimestamp("timestamp").toInstant();

          return new SnapshotEnvelope<>(ordering, appliedUpToOrdering, timestamp, snapshot);
        } else {
          throw new PersistenceException("Failed to insert snapshot: No result returned");
        }
      }
    } catch (final SQLException e) {
      if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
        throw new ConcurrentWriteException(partitionKey);
      }
      throw e;
    }
  }

  @Override
  public @NotNull JdbcExceptionTranslator exceptionTranslator() {
    return (partitionKey, cause) -> UNIQUE_VIOLATION_SQLSTATE.equals(cause.getSQLState())
        ? new ConcurrentWriteException(partitionKey) : new SaveFailedException(partitionKey, cause);
  }

  private <T> byte[] serialize(final @NotNull T event) {
    try (final var byteArrayOutputStream = new ByteArrayOutputStream(); final var objectOutputStream = new ObjectOutputStream(
        byteArrayOutputStream)) {
      objectOutputStream.writeObject(event);
      return byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      throw new PersistenceException("Failed to serialize event", e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> @NotNull T deserialize(final byte[] bytes) {
    try (final var byteArrayInputStream = new ByteArrayInputStream(
        bytes); final var objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
      return (T) objectInputStream.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new PersistenceException("Failed to deserialize event", e);
    }
  }
}
