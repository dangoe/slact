package de.dangoe.concurrent.slact.core.logging;

import org.jetbrains.annotations.NotNull;

/**
 * Renders log message templates with arguments.
 */
public interface LogTemplateRenderer {

  /**
   * Renders a message template with arguments.
   *
   * @param messageTemplate The template string.
   * @param args            Arguments to fill the template.
   * @return The rendered message.
   */
  @NotNull String render(@NotNull String messageTemplate, @NotNull Object... args);
}
