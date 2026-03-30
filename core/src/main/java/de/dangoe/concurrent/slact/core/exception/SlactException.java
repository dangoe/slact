package de.dangoe.concurrent.slact.core.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Base exception for actor system errors.
 */
public abstract class SlactException extends RuntimeException {

  /**
   * Constructs an exception instance with a message.
   *
   * @param message the error message.
   */
  protected SlactException(final @NotNull String message) {
    super(message);
  }

  /**
   * Constructs an exception instance with a message and cause.
   *
   * @param message the error message.
   * @param cause   the cause.
   */
  protected SlactException(final @NotNull String message, final @NotNull Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs an exception instance with a cause.
   *
   * @param cause the cause.
   */
  protected SlactException(final @NotNull Throwable cause) {
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
  protected SlactException(final @NotNull String message, final @NotNull Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
