// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.logging.internal;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SLF4J-backed implementation of {@link de.dangoe.concurrent.slact.core.logging.ActorLogger}. Log
 * records are emitted through the SLF4J logger bound to the actor's class.
 */
public final class Slf4jActorLogger extends AbstractActorLogger {

  private final @NotNull Logger logger;

  /**
   * Creates a new logger for the given actor.
   *
   * @param containerId the unique ID of the owning
   *                    {@link de.dangoe.concurrent.slact.core.SlactContainer}.
   * @param actorPath   the hierarchical path of the actor within the container.
   * @param actorClass  the concrete actor class whose SLF4J logger will be used.
   */
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