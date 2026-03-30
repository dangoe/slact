package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore;
import de.dangoe.concurrent.slact.persistence.SnapshotEnvelope;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import de.dangoe.concurrent.slact.persistence.exception.SaveFailedException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link JdbcEventStore} extension that additionally supports loading and saving snapshots.
 */
public class JdbcSnapshotCapableEventStore extends JdbcEventStore implements
    SnapshotCapableEventStore {

  /**
   * Creates a new JDBC-backed snapshot-capable event store.
   *
   * @param connectionPool  the pool from which JDBC connections are acquired.
   * @param executorService the executor used to run async database operations.
   * @param dialect         the dialect used to translate between domain objects and SQL.
   */
  public JdbcSnapshotCapableEventStore(final @NotNull JdbcConnectionPool connectionPool,
      final @NotNull ExecutorService executorService, final @NotNull JdbcDialect dialect) {
    super(connectionPool, executorService, dialect);
  }

  @Override
  public <S> @NotNull RichFuture<Optional<SnapshotEnvelope<S>>> loadLatestSnapshot(
      final @NotNull PartitionKey partitionKey, final @NotNull Class<S> snapshotType) {

    final var eventualResult = CompletableFuture.supplyAsync(() -> {

      try (final var connection = connectionPool.acquire()) {
        return dialect.<S>loadLatestSnapshot(connection, partitionKey);
      } catch (InterruptedException | SQLException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new PersistenceException(
            "Failed to load latest snapshot for partition key '%s'".formatted(partitionKey.raw()),
            e);
      }
    }, this.executorService);

    return RichFuture.of(eventualResult);
  }

  @Override
  public <S> @NotNull RichFuture<SnapshotEnvelope<S>> saveSnapshot(
      final @NotNull PartitionKey partitionKey, @Nullable Long lastSnapshotOrdering,
      final long appliedUpToOrdering, final @NotNull S snapshot) {

    final var eventualResult = CompletableFuture.supplyAsync(() -> {

      try (final var connection = connectionPool.acquire()) {
        try {
          connection.setAutoCommit(false);

          final var result = dialect.insertSnapshot(connection, partitionKey, lastSnapshotOrdering,
              appliedUpToOrdering, snapshot);

          connection.commit();

          return result;
        } catch (final Exception e) {
          connection.rollback();
          throw e;
        } finally {
          connection.setAutoCommit(true);
        }
      } catch (InterruptedException | SQLException e) {
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
