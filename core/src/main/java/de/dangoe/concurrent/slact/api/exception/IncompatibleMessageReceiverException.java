package de.dangoe.concurrent.slact.api.exception;

public class IncompatibleMessageReceiverException extends SlactException {

  public IncompatibleMessageReceiverException(String message) {
    super(message);
  }

  public IncompatibleMessageReceiverException(String message, Throwable cause) {
    super(message, cause);
  }

  public IncompatibleMessageReceiverException(Throwable cause) {
    super(cause);
  }

  public IncompatibleMessageReceiverException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
