package de.dangoe.concurrent.slact.memory;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Sealed interface for responses produced by memory actors.
 */
public sealed interface MemoryResponse
    permits MemoryResponse.Written, MemoryResponse.QueryResult, MemoryResponse.Failure {

  /**
   * Confirms that a memory was successfully written.
   *
   * @param memoryId the ID of the stored memory.
   */
  record Written(@NotNull UUID memoryId) implements MemoryResponse {

  }

  /**
   * Contains the results of a memory query.
   *
   * @param entries the list of matching memory entries, ordered by similarity score.
   */
  record QueryResult(@NotNull List<MemoryEntry> entries) implements MemoryResponse {

  }

  /**
   * Indicates that an operation failed.
   *
   * @param errorMessage a human-readable description of the failure.
   */
  record Failure(@NotNull String errorMessage) implements MemoryResponse {

  }
}
