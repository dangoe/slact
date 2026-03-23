package de.dangoe.concurrent.slact.persistence.exception;

import de.dangoe.concurrent.slact.core.exception.SlactException;
import org.jetbrains.annotations.NotNull;

public class PersistenceException extends SlactException {

  /**
   * Constructs an exception instance with a message.
   *
   * @param message The error message.
   */
  public PersistenceException(final @NotNull String message) {
    super(message);
  }

  /**
   * Constructs an exception instance with a message and cause.
   *
   * @param message The error message.
   * @param cause   The cause.
   */
  public PersistenceException(final @NotNull String message, final @NotNull Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs an exception instance with a cause.
   *
   * @param cause The cause.
   */
  public PersistenceException(final @NotNull Throwable cause) {
    super(cause);
  }

  /**
   * Constructs an exception instance with message, cause, suppression, and stack trace options.
   *
   * @param message            The error message.
   * @param cause              The cause.
   * @param enableSuppression  Whether suppression is enabled.
   * @param writableStackTrace Whether the stack trace is writable.
   */
  public PersistenceException(final @NotNull String message, final @NotNull Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
