// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.logging;

import org.jetbrains.annotations.NotNull;

/**
 * Renders log message templates with arguments.
 */
public interface LogTemplateRenderer {

  /**
   * Renders a message template with arguments.
   *
   * @param messageTemplate the template string.
   * @param args            arguments to fill the template.
   * @return the rendered message.
   */
  @NotNull String render(@NotNull String messageTemplate, @NotNull Object... args);
}
