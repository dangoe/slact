package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import com.zaxxer.hikari.HikariConfig;
import de.dangoe.concurrent.slact.persistence.EventStore;
import java.sql.SQLException;
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
class PostgreSqlJdbcEventStoreIT extends EventStoreSpec {

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
  protected @NotNull EventStore createEventStore() {
    return new JdbcEventStore(connectionPool, executorService, new PostgreSqlDialect());
  }

  @Override
  protected void cleanDatabase() throws Exception {
    try (final var connection = connectionPool.acquire();
        final var statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE events RESTART IDENTITY");
    } catch (final SQLException e) {
      throw new RuntimeException("Failed to clean database", e);
    }
  }
}
