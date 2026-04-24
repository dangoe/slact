// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import java.sql.Connection;
import org.jetbrains.annotations.NotNull;

/**
 * A connection pool for managing JDBC connections to a relational database. This interface defines
 * the contract for acquiring and releasing database connections.
 */
public interface JdbcConnectionPool extends AutoCloseable {

  /**
   * Acquires a connection from the pool. This method should block if no connections are currently
   * available until one becomes available.
   *
   * @return a JDBC connection from the pool.
   * @throws InterruptedException if the thread is interrupted while waiting for a connection.
   */
  @NotNull Connection acquire() throws InterruptedException;
}
