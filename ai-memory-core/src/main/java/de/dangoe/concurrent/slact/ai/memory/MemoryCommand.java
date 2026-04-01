package de.dangoe.concurrent.slact.ai.memory;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Sealed interface for commands sent to memory actors.
 */
public sealed interface MemoryCommand permits MemoryCommand.Memorize, MemoryCommand.Query {

  /**
   * Command to memorize a new memory entry.
   *
   * @param content   the text content to remember.
   * @param embedding the pre-computed embedding for the content.
   * @param metadata  optional key-value metadata.
   */
  record Memorize(
      @NotNull String content,
      @NotNull Embedding embedding,
      @NotNull Map<String, String> metadata) implements MemoryCommand {

  }

  /**
   * Command to query memories similar to the given embedding.
   *
   * @param embedding  the query embedding.
   * @param maxResults maximum number of results to return.
   */
  record Query(
      @NotNull Embedding embedding,
      int maxResults) implements MemoryCommand {

  }
}
