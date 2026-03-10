package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link JdbcConnectionPool} implementation backed by HikariCP.
 */
public class HikariConnectionPool implements JdbcConnectionPool {

  private final @NotNull HikariDataSource dataSource;

  public HikariConnectionPool(final @NotNull HikariConfig config) {
    this.dataSource = new HikariDataSource(config);
  }

  @Override
  public @NotNull Connection acquire() throws InterruptedException {
    try {
      return dataSource.getConnection();
    } catch (SQLException e) {
      if (e.getCause() instanceof InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw interrupted;
      }
      throw new RuntimeException("Failed to acquire connection from pool", e);
    }
  }

  @Override
  public void close() {
    dataSource.close();
  }
}
