package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;

/**
 * An implementation of the EventStore interface that uses JDBC to persist events in a relational
 * database.
 *
 * @param <E> The type of events that this event store will manage.
 */
public class JdbcEventStore<E> implements EventStore<E> {

  private final @NotNull JdbcConnectionPool connectionPool;
  private final @NotNull ExecutorService executorService;
  private final @NotNull JdbcDialect dialect;

  public JdbcEventStore(final @NotNull JdbcConnectionPool connectionPool,
      final @NotNull ExecutorService executorService,
      final @NotNull JdbcDialect dialect) {

    this.connectionPool = Objects.requireNonNull(connectionPool,
        "Connection pool must not be null");
    this.executorService = Objects.requireNonNull(executorService,
        "Executor service must not be null");
    this.dialect = Objects.requireNonNull(dialect, "Dialect must not be null");
  }

  @Override
  public @NotNull RichFuture<List<EventEnvelope<E>>> loadEvents(
      final @NotNull PartitionKey partitionKey) {

    final var eventualResult = CompletableFuture.supplyAsync(() -> {

      try (final var connection = connectionPool.acquire()) {
        return loadEvents(partitionKey, connection);
      } catch (InterruptedException | SQLException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new PersistenceException(
            "Failed to load events for partition key '%s'".formatted(partitionKey.value()), e);
      }
    }, this.executorService);

    return RichFuture.of(eventualResult);
  }

  @SuppressWarnings("unchecked")
  private @NotNull List<EventEnvelope<E>> loadEvents(final @NotNull PartitionKey partitionKey,
      final @NotNull Connection connection) throws SQLException {

    try (final var statement = connection.prepareStatement(
        "SELECT ordering, timestamp, payload FROM events WHERE partition_key = ? ORDER BY ordering ASC")) {

      statement.setString(1, partitionKey.value());

      try (final var resultSet = statement.executeQuery()) {

        final var events = new ArrayList<EventEnvelope<E>>();

        while (resultSet.next()) {

          final var ordering = resultSet.getLong("ordering");
          final var timestamp = resultSet.getTimestamp("timestamp").toInstant();
          final var event = (E) deserialize(resultSet.getBytes("payload"));
          final var eventEnvelope = new EventEnvelope<>(ordering, timestamp, event);

          events.add(eventEnvelope);
        }

        return events;
      }
    }
  }

  @Override
  public @NotNull RichFuture<List<EventEnvelope<E>>> appendMultiple(
      final @NotNull PartitionKey partitionKey, final @NotNull List<E> events) {

    final var eventualResult = CompletableFuture.supplyAsync(() -> {

      try (final var connection = connectionPool.acquire()) {
        return appendMultiple(partitionKey, events, connection);
      } catch (InterruptedException | SQLException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new PersistenceException(
            "Failed to append events for partition key '%s'".formatted(partitionKey.value()), e);
      }

    }, this.executorService);

    return RichFuture.of(eventualResult);
  }

  private @NotNull List<EventEnvelope<E>> appendMultiple(final @NotNull PartitionKey partitionKey,
      final @NotNull List<E> events, final @NotNull Connection connection) throws SQLException {

    return dialect.insertEvents(connection, partitionKey, events, this::serialize);
  }

  private byte[] serialize(final E event) {
    try (final var bos = new ByteArrayOutputStream();
        final var oos = new ObjectOutputStream(bos)) {
      oos.writeObject(event);
      return bos.toByteArray();
    } catch (IOException e) {
      throw new PersistenceException("Failed to serialize event", e);
    }
  }

  private Object deserialize(final byte[] bytes) {
    try (final var bis = new ByteArrayInputStream(bytes);
        final var ois = new ObjectInputStream(bis)) {
      return ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new PersistenceException("Failed to deserialize event", e);
    }
  }
}
