package de.dangoe.concurrent.slact.ai.memory.demo;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.ai.memory.MemoryCandidate;
import de.dangoe.concurrent.slact.ai.memory.MemoryExtractionPort;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

final class StubMemoryExtractionAdapter implements MemoryExtractionPort {

  @Override
  public @NotNull RichFuture<List<MemoryCandidate>> extract(final @NotNull String prompt,
      final @NotNull String response) {
    final var fact = response.length() > 80 ? response.substring(0, 80) : response;
    final var candidate = new MemoryCandidate("prompt", fact);
    return RichFuture.of(CompletableFuture.completedFuture(List.of(candidate)));
  }
}
