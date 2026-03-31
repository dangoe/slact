package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Port for calling the target language model with an enriched prompt.
 */
public interface TargetModelPort {

  /**
   * Calls the target language model with the given prompt and returns a future completing with the
   * response text.
   *
   * @param prompt the enriched prompt to send to the target model.
   * @return a future completing with the target model's response text.
   */
  @NotNull RichFuture<String> complete(@NotNull String prompt);
}
