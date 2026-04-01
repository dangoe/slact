package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Port for extracting candidate memories from a prompt and response.
 */
public interface MemoryExtractionPort {

  @NotNull RichFuture<List<MemoryCandidate>> extract(@NotNull String prompt,
      @NotNull String response);
}
