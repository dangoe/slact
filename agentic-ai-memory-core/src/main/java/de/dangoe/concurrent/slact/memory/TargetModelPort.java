package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Port for calling the target language model with an enriched prompt.
 */
public interface TargetModelPort {
  @NotNull RichFuture<String> complete(@NotNull String prompt);
}
