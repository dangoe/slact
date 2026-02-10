package de.dangoe.concurrent.slact.core.logging.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.logging.ActorLogger;
import de.dangoe.concurrent.slact.core.logging.LogTemplateRenderer;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractActorLogger implements ActorLogger {

  private final @NotNull UUID containerId;
  private final @NotNull ActorPath actorPath;

  protected final @NotNull LogTemplateRenderer renderer;

  protected AbstractActorLogger(final @NotNull UUID containerId,
      final @NotNull ActorPath actorPath) {

    this.containerId = Objects.requireNonNull(containerId, "Container ID must not be null!");
    this.actorPath = Objects.requireNonNull(actorPath, "Actor path must not be null!");

    this.renderer = new BraceTemplateRenderer();
  }

  @Override
  public final @NotNull UUID containerId() {
    return this.containerId;
  }

  @Override
  public final @NotNull ActorPath actorPath() {
    return this.actorPath;
  }

  protected final @NotNull String render(final @NotNull String template,
      final @NotNull Object... args) {

    return "[%s:%s] %s".formatted(this.containerId, this.actorPath,
        this.renderer.render(template, args));
  }
}