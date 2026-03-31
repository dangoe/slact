package de.dangoe.concurrent.slact.memory.demo;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.memory.MemoryCandidate;
import de.dangoe.concurrent.slact.memory.MemoryExtractionPort;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

final class StubMemoryExtractionPort implements MemoryExtractionPort {

  @Override
  public @NotNull RichFuture<List<MemoryCandidate>> extract(
      final @NotNull String prompt,
      final @NotNull String response) {
    final var fact = response.length() > 80 ? response.substring(0, 80) : response;
    final var candidate = new MemoryCandidate("prompt", fact);
    return RichFuture.of(CompletableFuture.completedFuture(List.of(candidate)));
  }
}
