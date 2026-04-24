// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence.exception;

import de.dangoe.concurrent.slact.core.exception.SlactException;
import org.jetbrains.annotations.NotNull;

/**
 * Base exception for all persistence-related failures.
 */
public class PersistenceException extends SlactException {

  /**
   * Constructs an exception instance with a message.
   *
   * @param message the error message.
   */
  public PersistenceException(final @NotNull String message) {
    super(message);
  }

  /**
   * Constructs an exception instance with a message and cause.
   *
   * @param message the error message.
   * @param cause   the cause.
   */
  public PersistenceException(final @NotNull String message, final @NotNull Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs an exception instance with a cause.
   *
   * @param cause the cause.
   */
  public PersistenceException(final @NotNull Throwable cause) {
    super(cause);
  }

  /**
   * Constructs an exception instance with message, cause, suppression, and stack trace options.
   *
   * @param message            the error message.
   * @param cause              the cause.
   * @param enableSuppression  whether suppression is enabled.
   * @param writableStackTrace whether the stack trace is writable.
   */
  public PersistenceException(final @NotNull String message, final @NotNull Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
