package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Strategy for persisting a {@link Memory} to a backing store.
 */
public interface MemorizationStrategy {

  /**
   * Memorizes the given memory and returns its assigned ID.
   *
   * @param memory the memory to persist.
   * @return a future completing with the stored memory's ID.
   */
  @NotNull RichFuture<UUID> memorize(@NotNull Memory memory);
}
