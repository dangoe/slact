package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import de.dangoe.concurrent.slact.persistence.exception.SaveFailedException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;

/**
 * An implementation of the EventStore interface that uses JDBC to persist events in a relational
 * database.
 */
public class JdbcEventStore implements EventStore {

  protected final @NotNull JdbcConnectionPool connectionPool;
  protected final @NotNull ExecutorService executorService;
  protected final @NotNull JdbcDialect dialect;

  public JdbcEventStore(final @NotNull JdbcConnectionPool connectionPool,
      final @NotNull ExecutorService executorService, final @NotNull JdbcDialect dialect) {

    this.connectionPool = Objects.requireNonNull(connectionPool,
        "Connection pool must not be null");
    this.executorService = Objects.requireNonNull(executorService,
        "Executor service must not be null");
    this.dialect = Objects.requireNonNull(dialect, "Dialect must not be null");
  }

  @Override
  public <E> @NotNull RichFuture<List<EventEnvelope<E>>> loadEvents(
      final @NotNull PartitionKey partitionKey, final long fromOrdering) {

    final var eventualResult = CompletableFuture.supplyAsync(() -> {

      try (final var connection = connectionPool.acquire()) {
        return dialect.<E>loadEvents(connection, partitionKey, fromOrdering);
      } catch (InterruptedException | SQLException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new PersistenceException(
            "Failed to load events for partition key '%s'".formatted(partitionKey.raw()), e);
      }
    }, this.executorService);

    return RichFuture.of(eventualResult);
  }

  @Override
  public <E> @NotNull RichFuture<List<EventEnvelope<E>>> appendMultiple(
      final @NotNull PartitionKey partitionKey, final long lastMaxOrdering,
      final @NotNull List<E> events) throws ConcurrentWriteException {

    final var eventualResult = CompletableFuture.supplyAsync(() -> {

      try (final var connection = this.connectionPool.acquire()) {
        try {
          connection.setAutoCommit(false);

          final var result = dialect.insertEvents(connection, partitionKey, lastMaxOrdering,
              events);

          connection.commit();

          return result;
        } catch (final Exception e) {
          connection.rollback();
          throw e;
        } finally {
          connection.setAutoCommit(true);
        }
      } catch (final InterruptedException | SQLException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw e instanceof SQLException sqle ? dialect.exceptionTranslator()
            .translate(partitionKey, sqle) : new SaveFailedException(partitionKey, e);
      }
    }, this.executorService);

    return RichFuture.of(eventualResult);
  }
}
