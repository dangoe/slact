package de.dangoe.concurrent.slact.core.logging;

import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Logger for actor-related events and messages.
 */
public interface ActorLogger {

  /**
   * Returns the container ID.
   *
   * @return The container UUID.
   */
  @NotNull UUID containerId();

  /**
   * Returns the path of the actor.
   *
   * @return The actor path.
   */
  @NotNull ActorPath actorPath();

  /**
   * Checks if trace logging is enabled.
   *
   * @return True if enabled.
   */
  boolean isTraceEnabled();

  /**
   * Checks if debug logging is enabled.
   *
   * @return True if enabled.
   */
  boolean isDebugEnabled();

  /**
   * Checks if info logging is enabled.
   *
   * @return True if enabled.
   */
  boolean isInfoEnabled();

  /**
   * Checks if warn logging is enabled.
   *
   * @return True if enabled.
   */
  boolean isWarnEnabled();

  /**
   * Checks if error logging is enabled.
   *
   * @return True if enabled.
   */
  boolean isErrorEnabled();

  /**
   * Logs a trace message.
   *
   * @param messageTemplate The message template.
   * @param args            Arguments for the template.
   */
  void trace(@NotNull String messageTemplate, @NotNull Object... args);

  /**
   * Logs a trace message with a cause.
   *
   * @param messageTemplate The message template.
   * @param cause           The throwable cause.
   * @param args            Arguments for the template.
   */
  void trace(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);

  /**
   * Logs a debug message.
   *
   * @param messageTemplate The message template.
   * @param args            Arguments for the template.
   */
  void debug(@NotNull String messageTemplate, @NotNull Object... args);

  /**
   * Logs a debug message with a cause.
   *
   * @param messageTemplate The message template.
   * @param cause           The throwable cause.
   * @param args            Arguments for the template.
   */
  void debug(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);

  /**
   * Logs an info message.
   *
   * @param messageTemplate The message template.
   * @param args            Arguments for the template.
   */
  void info(@NotNull String messageTemplate, @NotNull Object... args);

  /**
   * Logs an info message with a cause.
   *
   * @param messageTemplate The message template.
   * @param cause           The throwable cause.
   * @param args            Arguments for the template.
   */
  void info(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);

  /**
   * Logs a warning message.
   *
   * @param messageTemplate The message template.
   * @param args            Arguments for the template.
   */
  void warn(@NotNull String messageTemplate, @NotNull Object... args);

  /**
   * Logs a warning message with a cause.
   *
   * @param messageTemplate The message template.
   * @param cause           The throwable cause.
   * @param args            Arguments for the template.
   */
  void warn(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);

  /**
   * Logs an error message.
   *
   * @param messageTemplate The message template.
   * @param args            Arguments for the template.
   */
  void error(@NotNull String messageTemplate, @NotNull Object... args);

  /**
   * Logs an error message with a cause.
   *
   * @param messageTemplate The message template.
   * @param cause           The throwable cause.
   * @param args            Arguments for the template.
   */
  void error(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);
}