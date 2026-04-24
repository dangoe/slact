// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.logging.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.logging.ActorLogger;
import de.dangoe.concurrent.slact.core.logging.LogTemplateRenderer;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for actor loggers providing common functionality.
 */
public abstract class AbstractActorLogger implements ActorLogger {

  private final @NotNull UUID containerId;
  private final @NotNull ActorPath actorPath;

  /** the template renderer used by subclass logging methods. */
  protected final @NotNull LogTemplateRenderer renderer;

  /**
   * Constructs an AbstractActorLogger.
   *
   * @param containerId the container UUID.
   * @param actorPath   the actor path.
   */
  protected AbstractActorLogger(final @NotNull UUID containerId,
      final @NotNull ActorPath actorPath) {

    this.containerId = Objects.requireNonNull(containerId, "Container ID must not be null!");
    this.actorPath = Objects.requireNonNull(actorPath, "Actor path must not be null!");

    this.renderer = new BraceTemplateRenderer();
  }

  /**
   * Returns the container UUID.
   *
   * @return the container UUID.
   */
  @Override
  public final @NotNull UUID containerId() {
    return this.containerId;
  }

  /**
   * Returns the actor path.
   *
   * @return the actor path.
   */
  @Override
  public final @NotNull ActorPath actorPath() {
    return this.actorPath;
  }

  /**
   * Renders a formatted log message.
   *
   * @param template the message template.
   * @param args     arguments for the template.
   * @return the rendered message.
   */
  protected final @NotNull String render(final @NotNull String template,
      final @NotNull Object... args) {

    return "[%s:%s] %s".formatted(this.containerId, this.actorPath,
        this.renderer.render(template, args));
  }
}