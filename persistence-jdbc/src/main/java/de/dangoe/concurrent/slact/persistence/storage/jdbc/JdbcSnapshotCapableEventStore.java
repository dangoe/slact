package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore;
import de.dangoe.concurrent.slact.persistence.SnapshotEnvelope;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;

public class JdbcSnapshotCapableEventStore<E, S> extends JdbcEventStore<E> implements
    SnapshotCapableEventStore<E, S> {

  public JdbcSnapshotCapableEventStore(final @NotNull JdbcConnectionPool connectionPool,
      final @NotNull ExecutorService executorService, final @NotNull JdbcDialect dialect) {
    super(connectionPool, executorService, dialect);
  }

  @Override
  public @NotNull RichFuture<Optional<SnapshotEnvelope<S>>> loadLatestSnapshot(
      final @NotNull PartitionKey partitionKey) {

    final var eventualResult = CompletableFuture.supplyAsync(() -> {

      try (final var connection = connectionPool.acquire()) {
        return dialect.<S>loadLatestSnapshot(connection, partitionKey);
      } catch (InterruptedException | SQLException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new PersistenceException(
            "Failed to load latest snapshot for partition key '%s'".formatted(partitionKey.value()),
            e);
      }
    }, this.executorService);

    return RichFuture.of(eventualResult);
  }
}
