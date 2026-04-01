package de.dangoe.concurrent.slact.ai.memory.demo;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.ai.memory.Memory;
import de.dangoe.concurrent.slact.ai.memory.MemoryEntry;
import de.dangoe.concurrent.slact.ai.memory.MemoryQuery;
import de.dangoe.concurrent.slact.ai.memory.MemoryStore;
import de.dangoe.concurrent.slact.ai.memory.Score;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * Simple in-memory {@link MemoryStore} using cosine similarity for queries.
 */
final class InMemoryMemoryStore implements MemoryStore {

  private final List<Memory> memories = new CopyOnWriteArrayList<>();

  @Override
  public @NotNull RichFuture<UUID> save(final @NotNull Memory memory) {
    memories.add(memory);
    return RichFuture.of(CompletableFuture.completedFuture(memory.id()));
  }

  @Override
  public @NotNull RichFuture<List<MemoryEntry>> query(final @NotNull MemoryQuery query) {
    final var queryValues = query.embedding().values();
    final var results = memories.stream()
        .map(m -> new MemoryEntry(m,
            new Score(cosineSimilarity(queryValues, m.embedding().values()))))
        .sorted(Comparator.comparingDouble((MemoryEntry e) -> e.score().value()).reversed())
        .limit(query.maxResults())
        .toList();
    return RichFuture.of(CompletableFuture.completedFuture(new ArrayList<>(results)));
  }

  private static double cosineSimilarity(final float @NotNull [] a, final float @NotNull [] b) {
    if (a.length != b.length) {
      return 0.0;
    }
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    if (normA == 0.0 || normB == 0.0) {
      return 0.0;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
