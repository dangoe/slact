package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Port for storing and querying memories.
 */
public interface MemoryStore {

  /**
   * Persists a memory and returns its ID.
   *
   * @param memory the memory to store.
   * @return a future completing with the stored memory's ID.
   */
  @NotNull RichFuture<UUID> save(@NotNull Memory memory);

  /**
   * Queries memories similar to the given query and returns the top-K results.
   *
   * @param query the query with embedding and result count limit.
   * @return a future completing with the ordered list of matching memory entries.
   */
  @NotNull RichFuture<List<MemoryEntry>> query(@NotNull MemoryQuery query);
}
