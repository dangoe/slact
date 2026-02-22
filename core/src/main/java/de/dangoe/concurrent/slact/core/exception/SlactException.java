package de.dangoe.concurrent.slact.core.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Base exception for actor system errors.
 */
public abstract class SlactException extends RuntimeException {

  /**
   * Constructs an exception instance with a message.
   *
   * @param message The error message.
   */
  protected SlactException(final @NotNull String message) {
    super(message);
  }

  /**
   * Constructs an exception instance with a message and cause.
   *
   * @param message The error message.
   * @param cause   The cause.
   */
  protected SlactException(final @NotNull String message, final @NotNull Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs an exception instance with a cause.
   *
   * @param cause The cause.
   */
  protected SlactException(final @NotNull Throwable cause) {
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
  protected SlactException(final @NotNull String message, final @NotNull Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
