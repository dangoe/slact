package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for orchestrating the prompt processing pipeline, including embedding
 * generation, memory retrieval, target model completion, and memory extraction.
 */
public final class PromptOrchestratorActor extends Actor<PromptOrchestratorActor.Message> {

  private static final Logger logger = LoggerFactory.getLogger(PromptOrchestratorActor.class);

  /**
   * Messages handled by the {@link PromptOrchestratorActor}.
   */
  public sealed interface Message permits Message.Process, Message.PipelineDone {

    /**
     * Message to initiate the prompt processing pipeline with the given text.
     *
     * @param text the input prompt text to process.
     */
    record Process(@NotNull String text) implements Message {

    }

    /**
     * Message indicating that the prompt processing pipeline has completed, containing the result
     * and the original sender to reply to.
     *
     * @param result  the result of the prompt processing, either a successful answer or a failure
     * @param replyTo the original sender of the {@link Process} message to reply to with the
     *                result
     */
    record PipelineDone(@NotNull PromptResponse result,
                        @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }
  }

  private static final int DEFAULT_MAX_MEMORY_RESULTS = 5;

  private final @NotNull EmbeddingPort embeddingPort;
  private final @NotNull MemoryStore memoryStore;
  private final int maxMemoryResults;
  private final @NotNull TargetModelPort targetModelPort;
  private final @NotNull MemoryExtractionPort memoryExtractionPort;
  private final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor;

  /**
   * Creates a new PromptOrchestratorActor with the given dependencies.
   *
   * @param embeddingPort        the port for generating embeddings.
   * @param memoryStore          the memory store for querying and saving memories.
   * @param maxMemoryResults     the maximum number of memory results to retrieve for enriching the
   *                             prompt context.
   * @param targetModelPort      the port for completing the enriched prompt with the target model.
   * @param memoryExtractionPort the port for extracting new memories from the prompt and response.
   * @param memoryWriteActor     the actor handle for writing new memories to the memory store.
   */
  public PromptOrchestratorActor(final @NotNull EmbeddingPort embeddingPort,
      final @NotNull MemoryStore memoryStore, final int maxMemoryResults,
      final @NotNull TargetModelPort targetModelPort,
      final @NotNull MemoryExtractionPort memoryExtractionPort,
      final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor) {
    this.embeddingPort = Objects.requireNonNull(embeddingPort, "EmbeddingPort must not be null");
    this.memoryStore = Objects.requireNonNull(memoryStore, "MemoryStore must not be null");
    this.maxMemoryResults = maxMemoryResults;
    this.targetModelPort = Objects.requireNonNull(targetModelPort,
        "TargetModelPort must not be null");
    this.memoryExtractionPort = Objects.requireNonNull(memoryExtractionPort,
        "MemoryExtractionPort must not be null");
    this.memoryWriteActor = Objects.requireNonNull(memoryWriteActor,
        "MemoryWriteActor must not be null");
  }

  /**
   * Creates a new {@link PromptOrchestratorActor} with the given dependencies and a default
   * maxMemoryResults value.
   *
   * @param embeddingPort        the port for generating embeddings.
   * @param memoryStore          the memory store for querying and saving memories.
   * @param targetModelPort      the port for completing the enriched prompt with the target model.
   * @param memoryExtractionPort the port for extracting new memories from the prompt and response.
   * @param memoryWriteActor     the actor handle for writing new memories to the memory store.
   */
  public PromptOrchestratorActor(final @NotNull EmbeddingPort embeddingPort,
      final @NotNull MemoryStore memoryStore, final @NotNull TargetModelPort targetModelPort,
      final @NotNull MemoryExtractionPort memoryExtractionPort,
      final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor) {
    this(embeddingPort, memoryStore, DEFAULT_MAX_MEMORY_RESULTS, targetModelPort,
        memoryExtractionPort, memoryWriteActor);
  }

  @Override
  public void onMessage(final @NotNull Message message) {
    switch (message) {
      case Message.Process cmd -> handleProcess(cmd);
      case Message.PipelineDone done -> send(done.result()).to(done.replyTo());
    }
  }

  private void handleProcess(final @NotNull Message.Process cmd) {
    final ActorHandle<PromptResponse> replyTo = sender();
    final RichFuture<Message> pipeline = buildPipeline(cmd.text()).thenApply(
        result -> new Message.PipelineDone(result, replyTo));
    pipeFuture(pipeline).to(self());
  }

  private @NotNull RichFuture<PromptResponse> buildPipeline(final @NotNull String promptText) {
    return embeddingPort.embed(promptText).thenCompose(
        embedding -> memoryStore.query(new MemoryQuery(embedding, maxMemoryResults))
            .thenCompose(memories -> {
              final var enrichedPrompt = mergeContext(promptText, memories);
              return targetModelPort.complete(enrichedPrompt)
                  .thenCompose(response -> extractAndSaveMemoriesAsync(promptText, response)
                      .thenApply(unused -> (PromptResponse) new PromptResponse.Answer(response)));
            })).exceptionally(e -> new PromptResponse.Failure(e.getMessage()));
  }

  private @NotNull String mergeContext(final @NotNull String promptText,
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

  private @NotNull RichFuture<Void> extractAndSaveMemoriesAsync(final @NotNull String prompt,
      final @NotNull String response) {
    return memoryExtractionPort.extract(prompt, response)
        .thenCompose(this::saveExtractedMemories)
        .exceptionally(e -> {
          logger.warn("Memory extraction failed for prompt: {}", prompt, e);
          throw new RuntimeException(e);
        });
  }

  private @NotNull RichFuture<Void> saveExtractedMemories(
      final @NotNull List<MemoryCandidate> candidates) {

    RichFuture<Void> chain = RichFuture.of(CompletableFuture.completedFuture(null));
    for (final var candidate : candidates) {
      final var content = candidate.subject() + ": " + candidate.fact();
      chain = chain.thenCompose(unused -> embeddingPort.embed(content)
          .thenCompose(embedding -> memoryStore.save(Memory.of(content, embedding, Map.of()))
              .thenApply(savedId -> null)));
    }
    return chain;
  }
}
