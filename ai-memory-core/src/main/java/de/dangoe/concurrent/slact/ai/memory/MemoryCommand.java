package de.dangoe.concurrent.slact.ai.memory;

import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;


/**
 * Sealed interface for commands sent to memory actors.
 */
public sealed interface MemoryCommand permits MemoryCommand.Memorize, MemoryCommand.Query,
    MemoryCommand.Forget {

  /**
   * Command to memorize a new memory entry.
   *
   * <p>The concrete {@link MemorizationStrategy} decides how the content is persisted,
   * including any embedding computation or deduplication — those are implementation details not
   * visible at this level.
   *
   * @param content  the text content to remember.
   * @param metadata optional key-value metadata.
   */
  record Memorize(
      @NotNull String content,
      @NotNull Map<String, String> metadata) implements MemoryCommand {

  }

  /**
   * Command to query memories for a given topic.
   *
   * <p>The concrete {@link MemoryStrategy} decides how the topic is resolved into matching
   * memory entries — those are implementation details not visible at this level.
   *
   * @param topic      the subject or question to find relevant memories for.
   * @param maxResults maximum number of results to return.
   */
  record Query(
      @NotNull String topic,
      int maxResults) implements MemoryCommand {

  }

  /**
   * Command to delete a memory entry by its ID.
   *
   * @param memoryId the ID of the memory to delete.
   */
  record Forget(
      @NotNull UUID memoryId) implements MemoryCommand {

  }
}
