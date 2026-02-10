package de.dangoe.concurrent.slact.core.logging;

import org.jetbrains.annotations.NotNull;

public interface LogTemplateRenderer {

  @NotNull String render(@NotNull String messageTemplate, @NotNull Object... args);
}
