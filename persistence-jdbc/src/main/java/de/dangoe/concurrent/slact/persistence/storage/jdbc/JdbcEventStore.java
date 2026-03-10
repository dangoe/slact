package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.sql.SQLException;
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
        return dialect.<E>loadEvents(connection, partitionKey);
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

  @Override
  public @NotNull RichFuture<List<EventEnvelope<E>>> appendMultiple(
      final @NotNull PartitionKey partitionKey, final @NotNull List<E> events) {

    final var eventualResult = CompletableFuture.supplyAsync(() -> {

      try (final var connection = connectionPool.acquire()) {
        return dialect.insertEvents(connection, partitionKey, events);
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
}
