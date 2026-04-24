// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.sql.SQLException;
import org.jetbrains.annotations.NotNull;

/**
 * Translates a {@link SQLException} into a domain-level {@link PersistenceException} subtype. Each
 * {@link JdbcDialect} provides a translator that interprets vendor-specific SQL error codes and
 * states to surface the correct exception (e.g. {@code ConcurrentWriteException} for unique
 * constraint violations).
 */
@FunctionalInterface
public interface JdbcExceptionTranslator {

  /**
   * Translates the given {@link SQLException} to a {@link PersistenceException} appropriate for the
   * dialect.
   *
   * @param partitionKey the partition key involved in the failed operation.
   * @param cause        the SQL exception to translate.
   * @return a {@link PersistenceException} (or subtype) representing the failure.
   */
  @NotNull PersistenceException translate(@NotNull PartitionKey partitionKey,
      @NotNull SQLException cause);
}
