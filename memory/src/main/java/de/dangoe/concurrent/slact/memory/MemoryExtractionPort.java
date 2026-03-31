package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface MemoryExtractionPort {
  @NotNull RichFuture<List<MemoryCandidate>> extract(@NotNull String prompt, @NotNull String response);
}
