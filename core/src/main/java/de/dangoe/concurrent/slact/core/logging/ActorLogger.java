package de.dangoe.concurrent.slact.core.logging;

import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public interface ActorLogger {

  @NotNull UUID containerId();

  @NotNull ActorPath actorPath();

  boolean isTraceEnabled();

  boolean isDebugEnabled();

  boolean isInfoEnabled();

  boolean isWarnEnabled();

  boolean isErrorEnabled();

  void trace(@NotNull String messageTemplate, @NotNull Object... args);

  void trace(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);

  void debug(@NotNull String messageTemplate, @NotNull Object... args);

  void debug(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);

  void info(@NotNull String messageTemplate, @NotNull Object... args);

  void info(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);

  void warn(@NotNull String messageTemplate, @NotNull Object... args);

  void warn(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);

  void error(@NotNull String messageTemplate, @NotNull Object... args);

  void error(@NotNull String messageTemplate, @NotNull Throwable cause, @NotNull Object... args);
}