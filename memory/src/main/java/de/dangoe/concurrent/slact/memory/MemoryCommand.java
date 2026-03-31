package de.dangoe.concurrent.slact.memory;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Sealed interface for commands sent to {@link MemoryActor}.
 */
public sealed interface MemoryCommand permits MemoryCommand.WriteMemory, MemoryCommand.QueryMemory {

  /**
   * Command to write a new memory entry.
   *
   * @param content   the text content to remember.
   * @param embedding the pre-computed embedding vector for the content.
   * @param metadata  optional key-value metadata.
   */
  record WriteMemory(
      @NotNull String content,
      float @NotNull [] embedding,
      @NotNull Map<String, String> metadata) implements MemoryCommand {}

  /**
   * Command to query memories similar to the given embedding.
   *
   * @param embedding the query embedding vector.
   * @param topK      maximum number of results to return.
   */
  record QueryMemory(
      float @NotNull [] embedding,
      int topK) implements MemoryCommand {}
}
