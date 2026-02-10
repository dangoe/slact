package de.dangoe.concurrent.slact.core.logging.internal;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Slf4jActorLogger extends AbstractActorLogger {

  private final @NotNull Logger logger;

  public Slf4jActorLogger(final @NotNull UUID containerId, final @NotNull ActorPath actorPath,
      final @NotNull Class<? extends Actor<?>> actorClass) {
    super(containerId, actorPath);
    this.logger = LoggerFactory.getLogger(actorClass);
  }

  @Override
  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  @Override
  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  @Override
  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  @Override
  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  @Override
  public boolean isErrorEnabled() {
    return logger.isErrorEnabled();
  }

  @Override
  public void trace(final @NotNull String messageTemplate, final @NotNull Object... args) {
    this.logger.trace(render(messageTemplate, args));
  }

  @Override
  public void trace(final @NotNull String messageTemplate, final @NotNull Throwable cause,
      final @NotNull Object... args) {
    this.logger.trace(render(messageTemplate, args), cause);
  }

  @Override
  public void debug(final @NotNull String messageTemplate, final @NotNull Object... args) {
    this.logger.debug(render(messageTemplate, args));
  }

  @Override
  public void debug(final @NotNull String messageTemplate, final @NotNull Throwable cause,
      final @NotNull Object... args) {
    this.logger.debug(render(messageTemplate, args), cause);
  }

  @Override
  public void info(final @NotNull String messageTemplate, final @NotNull Object... args) {
    this.logger.info(render(messageTemplate, args));
  }

  @Override
  public void info(final @NotNull String messageTemplate, final @NotNull Throwable cause,
      final @NotNull Object... args) {
    this.logger.info(render(messageTemplate, args), cause);
  }

  @Override
  public void warn(final @NotNull String messageTemplate, final @NotNull Object... args) {
    this.logger.warn(render(messageTemplate, args));
  }

  @Override
  public void warn(final @NotNull String messageTemplate, final @NotNull Throwable cause,
      final @NotNull Object... args) {
    this.logger.warn(render(messageTemplate, args), cause);
  }

  @Override
  public void error(final @NotNull String messageTemplate, final @NotNull Object... args) {
    this.logger.error(render(messageTemplate, args));
  }

  @Override
  public void error(final @NotNull String messageTemplate, final @NotNull Throwable cause,
      final @NotNull Object... args) {
    this.logger.error(render(messageTemplate, args), cause);
  }
}