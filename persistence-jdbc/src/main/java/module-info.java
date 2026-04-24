// SPDX-License-Identifier: MIT OR Apache-2.0

/**
 * slact JDBC persistence storage.
 */
module de.dangoe.concurrent.slact.persistence.jdbc {
  exports de.dangoe.concurrent.slact.persistence.storage.jdbc;
  requires transitive de.dangoe.concurrent.slact.persistence;
  requires transitive java.sql;
  requires com.zaxxer.hikari;
  requires org.jetbrains.annotations;
}
