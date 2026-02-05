package de.dangoe.concurrent.slact.core.exception;

import org.jetbrains.annotations.NotNull;

public abstract class SlactException extends RuntimeException {

  protected SlactException(final @NotNull String message) {
    super(message);
  }

  protected SlactException(final @NotNull String message, final @NotNull Throwable cause) {
    super(message, cause);
  }

  protected SlactException(final @NotNull Throwable cause) {
    super(cause);
  }

  protected SlactException(final @NotNull String message, final @NotNull Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
