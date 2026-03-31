package de.dangoe.concurrent.slact.memory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a single memory entry with content, a pre-computed embedding vector, and
 * optional metadata.
 *
 * @param id         unique identifier.
 * @param content    the raw text content of the memory.
 * @param embedding  the pre-computed embedding vector for the content.
 * @param metadata   optional key-value metadata.
 * @param createdAt  creation timestamp.
 */
public record Memory(
    @NotNull String id,
    @NotNull String content,
    float @NotNull [] embedding,
    @NotNull Map<String, String> metadata,
    @NotNull Instant createdAt) {

  /**
   * Creates a new memory with a generated ID and the current timestamp.
   *
   * @param content   the raw text content.
   * @param embedding the pre-computed embedding vector.
   * @param metadata  optional key-value metadata.
   * @return a new memory instance.
   */
  public static @NotNull Memory of(
      @NotNull String content,
      float @NotNull [] embedding,
      @NotNull Map<String, String> metadata) {
    return new Memory(UUID.randomUUID().toString(), content, embedding,
        Map.copyOf(metadata), Instant.now());
  }
}
