package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
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
  public sealed interface Message permits Message.Process, Message.PipelineDone,
      Message.TriggerMemoryUpdate, Message.ProcessNextCandidate, Message.CandidateEmbeddingDone,
      Message.MemoryUpdateFailed {

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
    record PipelineDone(@NotNull String prompt, @NotNull PromptResponse result,
                        @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }

    record TriggerMemoryUpdate(@NotNull String prompt, @NotNull String response) implements
        Message {

    }

    record ProcessNextCandidate(@NotNull String prompt, @NotNull List<MemoryCandidate> candidates,
                                int index) implements Message {

    }

    record CandidateEmbeddingDone(@NotNull String prompt, @NotNull List<MemoryCandidate> candidates,
                                  int index, @NotNull String content,
                                  @NotNull Embedding embedding) implements Message {

    }

    record MemoryUpdateFailed(@NotNull String prompt, @NotNull String reason) implements Message {

    }
  }

  private static final int DEFAULT_MAX_MEMORY_RESULTS = 5;

  private final @NotNull EmbeddingPort embeddingPort;
  private final @NotNull ActorHandle<MemoryCommand.QueryMemory> memoryRetrievalActor;
  private final int maxMemoryResults;
  private final @NotNull TargetModelPort targetModelPort;
  private final @NotNull MemoryExtractionPort memoryExtractionPort;
  private final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor;

  /**
   * Creates a new PromptOrchestratorActor with the given dependencies.
   *
   * @param embeddingPort        the port for generating embeddings.
   * @param memoryRetrievalActor the actor handle for querying memories.
   * @param maxMemoryResults     the maximum number of memory results to retrieve for enriching the
   *                             prompt context.
   * @param targetModelPort      the port for completing the enriched prompt with the target model.
   * @param memoryExtractionPort the port for extracting new memories from the prompt and response.
   * @param memoryWriteActor     the actor handle for writing new memories to the memory store.
   */
  public PromptOrchestratorActor(final @NotNull EmbeddingPort embeddingPort,
      final @NotNull ActorHandle<MemoryCommand.QueryMemory> memoryRetrievalActor,
      final int maxMemoryResults, final @NotNull TargetModelPort targetModelPort,
      final @NotNull MemoryExtractionPort memoryExtractionPort,
      final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor) {
    this.embeddingPort = Objects.requireNonNull(embeddingPort, "EmbeddingPort must not be null");
    this.memoryRetrievalActor = Objects.requireNonNull(memoryRetrievalActor,
        "MemoryRetrievalActor must not be null");
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
   * @param memoryRetrievalActor the actor handle for querying memories.
   * @param targetModelPort      the port for completing the enriched prompt with the target model.
   * @param memoryExtractionPort the port for extracting new memories from the prompt and response.
   * @param memoryWriteActor     the actor handle for writing new memories to the memory store.
   */
  public PromptOrchestratorActor(final @NotNull EmbeddingPort embeddingPort,
      final @NotNull ActorHandle<MemoryCommand.QueryMemory> memoryRetrievalActor,
      final @NotNull TargetModelPort targetModelPort,
      final @NotNull MemoryExtractionPort memoryExtractionPort,
      final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor) {
    this(embeddingPort, memoryRetrievalActor, DEFAULT_MAX_MEMORY_RESULTS, targetModelPort,
        memoryExtractionPort, memoryWriteActor);
  }

  @Override
  public void onMessage(final @NotNull Message message) {
    switch (message) {
      case Message.Process cmd -> handleProcess(cmd);
      case Message.PipelineDone done -> handlePipelineDone(done);
      case Message.TriggerMemoryUpdate update -> handleTriggerMemoryUpdate(update);
      case Message.ProcessNextCandidate nextCandidate -> handleProcessNextCandidate(nextCandidate);
      case Message.CandidateEmbeddingDone embedded -> handleCandidateEmbeddingDone(embedded);
      case Message.MemoryUpdateFailed failed ->
          logger.warn("Memory update failed for prompt: {} ({})", failed.prompt(), failed.reason());
    }
  }

  private void handleProcess(final @NotNull Message.Process cmd) {
    final ActorHandle<PromptResponse> replyTo = sender();
    final RichFuture<Message> pipeline = buildPipeline(cmd.text()).thenApply(
        result -> new Message.PipelineDone(cmd.text(), result, replyTo));
    pipeFuture(pipeline).to(self());
  }

  private @NotNull RichFuture<PromptResponse> buildPipeline(final @NotNull String promptText) {
    return embeddingPort.embed(promptText)
        .thenCompose(embedding -> askMemoryQuery(embedding).thenCompose(memories -> {
          final var enrichedPrompt = mergeContext(promptText, memories);
          return targetModelPort.complete(enrichedPrompt)
              .thenApply(response -> (PromptResponse) new PromptResponse.Answer(response));
        })).exceptionally(e -> new PromptResponse.Failure(e.getMessage()));
  }

  private void handlePipelineDone(final @NotNull Message.PipelineDone done) {
    send(done.result()).to(done.replyTo());
    if (done.result() instanceof PromptResponse.Answer(String text)) {
      send((Message) new Message.TriggerMemoryUpdate(done.prompt(), text)).to(self());
    }
  }

  private void handleTriggerMemoryUpdate(final @NotNull Message.TriggerMemoryUpdate update) {
    final RichFuture<Message> extractionFlow = memoryExtractionPort.extract(update.prompt(),
            update.response()).thenApply(
            candidates -> (Message) new Message.ProcessNextCandidate(update.prompt(), candidates, 0))
        .exceptionally(
            e -> new Message.MemoryUpdateFailed(update.prompt(), Objects.toString(e.getMessage())));
    pipeFuture(extractionFlow).to(self());
  }

  private void handleProcessNextCandidate(final @NotNull Message.ProcessNextCandidate next) {
    if (next.index() >= next.candidates().size()) {
      return;
    }

    final var candidate = next.candidates().get(next.index());
    final var content = candidate.subject() + ": " + candidate.fact();

    final RichFuture<Message> embeddingFlow = embeddingPort.embed(content).thenApply(
        embedding -> (Message) new Message.CandidateEmbeddingDone(next.prompt(), next.candidates(),
            next.index(), content, embedding)).exceptionally(
        e -> new Message.MemoryUpdateFailed(next.prompt(), Objects.toString(e.getMessage())));
    pipeFuture(embeddingFlow).to(self());
  }

  private void handleCandidateEmbeddingDone(
      final @NotNull Message.CandidateEmbeddingDone embedded) {
    final RichFuture<Message> writeFlow = askMemoryWrite(embedded.content(),
        embedded.embedding()).thenApply(
        unused -> (Message) new Message.ProcessNextCandidate(embedded.prompt(),
            embedded.candidates(), embedded.index() + 1)).exceptionally(
        e -> new Message.MemoryUpdateFailed(embedded.prompt(), Objects.toString(e.getMessage())));
    pipeFuture(writeFlow).to(self());
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

  private @NotNull RichFuture<List<MemoryEntry>> askMemoryQuery(
      final @NotNull Embedding embedding) {
    return asRichFuture(
        context().requestResponseTo(new MemoryCommand.QueryMemory(embedding, maxMemoryResults))
            .ofType(MemoryResponse.class).from(memoryRetrievalActor)).thenCompose(response -> {
      if (response instanceof MemoryResponse.QueryResult(List<MemoryEntry> entries)) {
        return RichFuture.of(CompletableFuture.completedFuture(entries));
      }
      if (response instanceof MemoryResponse.Failure(String errorMessage)) {
        return RichFuture.of(
            CompletableFuture.failedFuture(new IllegalStateException(errorMessage)));
      }
      return RichFuture.of(CompletableFuture.failedFuture(
          new IllegalStateException("Unexpected response type: " + response.getClass().getName())));
    });
  }

  private @NotNull RichFuture<Void> askMemoryWrite(final @NotNull String content,
      final @NotNull Embedding embedding) {
    return asRichFuture(
        context().requestResponseTo(new MemoryCommand.WriteMemory(content, embedding, Map.of()))
            .ofType(MemoryResponse.class).from(memoryWriteActor)).thenCompose(response -> {
      if (response instanceof MemoryResponse.Written) {
        return RichFuture.of(CompletableFuture.completedFuture(null));
      }
      if (response instanceof MemoryResponse.Failure(String errorMessage)) {
        return RichFuture.of(
            CompletableFuture.failedFuture(new IllegalStateException(errorMessage)));
      }
      return RichFuture.of(CompletableFuture.failedFuture(
          new IllegalStateException("Unexpected response type: " + response.getClass().getName())));
    });
  }

  @SuppressWarnings("unchecked")
  private static <T> @NotNull RichFuture<T> asRichFuture(final @NotNull Future<T> future) {
    if (future instanceof RichFuture<?> richFuture) {
      return (RichFuture<T>) richFuture;
    }
    return RichFuture.of(CompletableFuture.failedFuture(
        new IllegalStateException("Expected RichFuture from actor ask operation.")));
  }
}
