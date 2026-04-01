package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Actor that merges retrieved memories into an enriched prompt and forwards it to the next actor in
 * the pipeline.
 */
public final class ContextMergeActor extends Actor<ContextMergeActor.Message> {

  /**
   * Messages handled by {@link ContextMergeActor}.
   */
  public sealed interface Message permits Message.Merge {

    /**
     * Merges the given memories into the original prompt, then sends the enriched prompt to
     * {@code nextActor}; the final answer will be delivered to {@code replyTo}.
     *
     * @param originalPrompt the raw prompt text.
     * @param memories       context memories retrieved for this prompt.
     * @param nextActor      the actor that will invoke the LLM.
     * @param replyTo        where the final {@link PromptResponse} should be delivered.
     */
    record Merge(
        @NotNull String originalPrompt,
        @NotNull List<MemoryEntry> memories,
        @NotNull ActorHandle<LlmCallActor.Message> nextActor,
        @NotNull ActorHandle<PromptResponse> replyTo)
        implements Message {

    }
  }

  @Override
  public void onMessage(final @NotNull Message message) {
    switch (message) {
      case Message.Merge merge -> {
        final var enriched = mergeContext(merge.originalPrompt(), merge.memories());
        send((LlmCallActor.Message) new LlmCallActor.Message.Call(enriched, merge.replyTo())).to(merge.nextActor());
      }
    }
  }

  private @NotNull String mergeContext(
      final @NotNull String promptText,
      final @NotNull List<MemoryEntry> memories) {
    if (memories.isEmpty()) {
      return promptText;
    }
    final var sb = new StringBuilder("[Context:\n");
    for (final var entry : memories) {
      sb.append("- ").append(entry.memory().content()).append('\n');
    }
    return sb.append("]\n\n").append(promptText).toString();
  }
}
