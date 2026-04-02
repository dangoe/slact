package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.Actor;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Actor that delegates all memory operations to a {@link MemoryStrategy}.
 *
 * <p>This actor is a thin delegation layer: it accepts {@link MemoryCommand} messages and
 * forwards each to the strategy, which owns all storage and embedding details.</p>
 */
public final class MemoryActor extends Actor<MemoryCommand> {

  private final @NotNull MemoryStrategy strategy;

  /**
   * Creates a new {@link MemoryActor} with the given strategy.
   *
   * @param strategy the strategy to delegate all memory operations to; must not be {@code null}.
   */
  public MemoryActor(final @NotNull MemoryStrategy strategy) {
    this.strategy = Objects.requireNonNull(strategy, "Memory strategy must not be null");
  }

  @Override
  public void onMessage(final @NotNull MemoryCommand message) {
    switch (message) {
      case MemoryCommand.Memorize cmd -> handleMemorize(cmd);
      case MemoryCommand.Query cmd -> handleQuery(cmd);
      case MemoryCommand.Forget cmd -> handleForget(cmd);
    }
  }

  private void handleMemorize(final @NotNull MemoryCommand.Memorize cmd) {
    try {
      final var id = strategy.memorize(cmd.content(), cmd.metadata()).join();
      respondWith(new MemoryResponse.Written(id));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }

  private void handleQuery(final @NotNull MemoryCommand.Query cmd) {
    try {
      final var entries = strategy.retrieve(cmd.topic(), cmd.maxResults()).join();
      respondWith(new MemoryResponse.QueryResult(entries));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }

  private void handleForget(final @NotNull MemoryCommand.Forget cmd) {
    try {
      strategy.delete(cmd.memoryId()).join();
      respondWith(new MemoryResponse.Forgotten(cmd.memoryId()));
    } catch (final Exception e) {
      respondWith(new MemoryResponse.Failure(e.getMessage()));
    }
  }
}
