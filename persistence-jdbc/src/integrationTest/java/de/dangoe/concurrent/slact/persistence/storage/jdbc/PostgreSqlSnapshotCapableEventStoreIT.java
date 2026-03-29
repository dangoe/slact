package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import com.zaxxer.hikari.HikariConfig;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore;
import de.dangoe.concurrent.slact.persistence.testkit.SnapshotCapableEventStoreSpec;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgreSqlSnapshotCapableEventStoreIT extends SnapshotCapableEventStoreSpec {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  private static HikariConnectionPool connectionPool;
  private static ExecutorService executorService;

  @BeforeAll
  static void setUpDatabase() throws Exception {
    final var config = new HikariConfig();
    config.setJdbcUrl(postgres.getJdbcUrl());
    config.setUsername(postgres.getUsername());
    config.setPassword(postgres.getPassword());
    connectionPool = new HikariConnectionPool(config);

    try (final var connection = connectionPool.acquire()) {
      final var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
          new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
      liquibase.update();
    }

    executorService = Executors.newFixedThreadPool(4);
  }

  @AfterAll
  static void tearDownDatabase() {
    executorService.shutdown();
    connectionPool.close();
  }

  @Override
  protected @NotNull SnapshotCapableEventStore createSnapshotCapableEventStore() {
    return new JdbcSnapshotCapableEventStore(connectionPool, executorService,
        new PostgreSqlDialect());
  }

  @Override
  protected void cleanDatabase() throws Exception {
    try (final var connection = connectionPool.acquire();
        final var statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE events RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE snapshots");
    } catch (final SQLException e) {
      throw new RuntimeException("Failed to clean database", e);
    }
  }

  @Override
  protected void seedSnapshot(final @NotNull PartitionKey key, final long ordering,
      final long appliedUpToOrdering, final @NotNull TestSnapshot snapshot) throws Exception {
    final byte[] snapshotPayload;
    try (final var baos = new ByteArrayOutputStream();
        final var oos = new ObjectOutputStream(baos)) {
      oos.writeObject(snapshot);
      snapshotPayload = baos.toByteArray();
    }

    try (final var connection = connectionPool.acquire()) {
      connection.setAutoCommit(false);
      try {
        try (final var stmt = connection.prepareStatement(
            "INSERT INTO snapshots (partition_key, ordering, event_ordering, timestamp, payload) VALUES (?, ?, ?, ?, ?)")) {
          stmt.setString(1, key.raw());
          stmt.setLong(2, ordering);
          stmt.setLong(3, appliedUpToOrdering);
          stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
          stmt.setBytes(5, snapshotPayload);
          stmt.executeUpdate();
        }
        connection.commit();
      } catch (final Exception e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(true);
      }
    }
  }
}
