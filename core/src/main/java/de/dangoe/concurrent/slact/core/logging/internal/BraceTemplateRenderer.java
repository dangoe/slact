// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.logging.internal;

import de.dangoe.concurrent.slact.core.logging.LogTemplateRenderer;
import org.jetbrains.annotations.NotNull;

final class BraceTemplateRenderer implements LogTemplateRenderer {

  @Override
  public @NotNull String render(final @NotNull String messageTemplate,
      final @NotNull Object... args) {

    if (args == null || args.length == 0) {
      return messageTemplate;
    }

    final var sb = new StringBuilder(messageTemplate.length() + 32);
    final var templateLength = messageTemplate.length();

    int argIndex = 0;

    for (int i = 0; i < templateLength; i++) {
      final var c = messageTemplate.charAt(i);

      if (c == '{' && i + 1 < templateLength && messageTemplate.charAt(i + 1) == '}'
          && argIndex < args.length) {

        sb.append(args[argIndex++]);
        i++;
      } else {
        sb.append(c);
      }
    }

    return sb.toString();
  }
}