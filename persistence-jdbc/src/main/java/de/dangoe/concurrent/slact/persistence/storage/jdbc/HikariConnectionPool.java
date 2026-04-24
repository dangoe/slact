// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.sql.Connection;
import java.sql.SQLException;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link JdbcConnectionPool} implementation backed by HikariCP.
 */
public class HikariConnectionPool implements JdbcConnectionPool {

  private final @NotNull HikariDataSource dataSource;

  /**
   * Creates a HikariCP-backed connection pool.
   *
   * @param config HikariCP configuration used to create the underlying data source
   */
  public HikariConnectionPool(final @NotNull HikariConfig config) {
    this.dataSource = new HikariDataSource(config);
  }

  @Override
  public @NotNull Connection acquire() throws InterruptedException {
    try {
      return dataSource.getConnection();
    } catch (final SQLException e) {
      if (e.getCause() instanceof InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw interrupted;
      }
      throw new PersistenceException("Failed to acquire connection from pool", e);
    }
  }

  @Override
  public void close() {
    dataSource.close();
  }
}
