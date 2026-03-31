package de.dangoe.concurrent.slact.memory.demo;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.memory.Embedding;
import de.dangoe.concurrent.slact.memory.EmbeddingPort;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

final class StubEmbeddingPort implements EmbeddingPort {

  @Override
  public @NotNull RichFuture<Embedding> embed(final @NotNull String text) {
    final int hash = text.hashCode();
    final float[] values = {
        Math.abs(hash % 100) / 100.0f,
        Math.abs((hash >> 8) % 100) / 100.0f,
        Math.abs((hash >> 16) % 100) / 100.0f,
        Math.abs((hash >> 24) % 100) / 100.0f
    };
    return RichFuture.of(CompletableFuture.completedFuture(new Embedding(values)));
  }
}
