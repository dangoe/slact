package de.dangoe.concurrent.slact.exception;

public abstract class SlactException extends RuntimeException{

  protected SlactException(String message) {
    super(message);
  }

  protected SlactException(String message, Throwable cause) {
    super(message, cause);
  }

  protected SlactException(Throwable cause) {
    super(cause);
  }

  protected SlactException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
