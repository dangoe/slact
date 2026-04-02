package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for orchestrating the prompt processing pipeline, including embedding
 * generation, memory retrieval, context merging, LLM completion, and memory extraction.
 */
public final class PromptOrchestratorActor extends Actor<PromptOrchestratorActor.Message> {

  private static final Logger logger = LoggerFactory.getLogger(PromptOrchestratorActor.class);

  private static final AtomicLong bridgeCounter = new AtomicLong();

  /**
   * Messages handled by the {@link PromptOrchestratorActor}.
   */
  public sealed interface Message permits Message.Process, Message.PipelineDone,
      Message.EmbeddingReady, Message.EmbeddingFailed, Message.MemoryQueryReady,
      Message.MemoryQueryFailed, Message.TriggerContextMerge, Message.TriggerMemoryUpdate,
      Message.ProcessNextCandidate, Message.MemoryUpdateFailed {

    record Process(@NotNull String text) implements Message {

    }

    record PipelineDone(@NotNull String prompt, @NotNull PromptResponse result,
                        @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }

    record EmbeddingReady(@NotNull String prompt, @NotNull Embedding embedding,
                          @NotNull ActorHandle<PromptResponse> replyTo,
                          @NotNull ActorHandle<PromptResponse> pipelineReplyTo) implements Message {

    }

    record EmbeddingFailed(@NotNull String prompt, @NotNull Throwable cause,
                           @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }

    record MemoryQueryReady(@NotNull String prompt, @NotNull List<MemoryEntry> memories,
                            @NotNull ActorHandle<PromptResponse> replyTo,
                            @NotNull ActorHandle<PromptResponse> pipelineReplyTo)
        implements Message {

    }

    record MemoryQueryFailed(@NotNull String prompt, @NotNull Throwable cause,
                             @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }

    record TriggerContextMerge(@NotNull String prompt, @NotNull List<MemoryEntry> memories,
                               @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }

    record TriggerMemoryUpdate(@NotNull String prompt, @NotNull String response) implements
        Message {

    }

    record ProcessNextCandidate(@NotNull String prompt, @NotNull List<MemoryCandidate> candidates,
                                int index) implements Message {

    }

    record MemoryUpdateFailed(@NotNull String prompt, @NotNull String reason) implements Message {

    }
  }

  private static final int DEFAULT_MAX_MEMORY_RESULTS = 5;

  private final @NotNull EmbeddingPort embeddingPort;
  private final @NotNull ActorHandle<MemoryCommand> memoryActor;
  private final int maxMemoryResults;
  private final @NotNull ActorHandle<ContextMergeActor.Message> contextMergeActor;
  private final @NotNull ActorHandle<LlmCallActor.Message> llmCallActor;
  private final @NotNull MemoryExtractionPort memoryExtractionPort;

  public PromptOrchestratorActor(final @NotNull EmbeddingPort embeddingPort,
      final @NotNull ActorHandle<MemoryCommand> memoryActor, final int maxMemoryResults,
      final @NotNull ActorHandle<ContextMergeActor.Message> contextMergeActor,
      final @NotNull ActorHandle<LlmCallActor.Message> llmCallActor,
      final @NotNull MemoryExtractionPort memoryExtractionPort) {

    this.embeddingPort = Objects.requireNonNull(embeddingPort, "EmbeddingPort must not be null");
    this.memoryActor = Objects.requireNonNull(memoryActor, "MemoryActor must not be null");
    this.maxMemoryResults = maxMemoryResults;
    this.contextMergeActor = Objects.requireNonNull(contextMergeActor,
        "ContextMergeActor must not be null");
    this.llmCallActor = Objects.requireNonNull(llmCallActor, "LlmCallActor must not be null");
    this.memoryExtractionPort = Objects.requireNonNull(memoryExtractionPort,
        "MemoryExtractionPort must not be null");
  }

  public PromptOrchestratorActor(final @NotNull EmbeddingPort embeddingPort,
      final @NotNull ActorHandle<MemoryCommand> memoryActor,
      final @NotNull ActorHandle<ContextMergeActor.Message> contextMergeActor,
      final @NotNull ActorHandle<LlmCallActor.Message> llmCallActor,
      final @NotNull MemoryExtractionPort memoryExtractionPort) {
    this(embeddingPort, memoryActor, DEFAULT_MAX_MEMORY_RESULTS, contextMergeActor, llmCallActor,
        memoryExtractionPort);
  }

  @Override
  public void onMessage(final @NotNull Message message) {
    switch (message) {
      case Message.Process cmd -> handleProcess(cmd);
      case Message.PipelineDone done -> handlePipelineDone(done);
      case Message.EmbeddingReady embeddingReady -> handleEmbeddingReady(embeddingReady);
      case Message.EmbeddingFailed embeddingFailed -> handleEmbeddingFailed(embeddingFailed);
      case Message.MemoryQueryReady memoryQueryReady -> handleMemoryQueryReady(memoryQueryReady);
      case Message.MemoryQueryFailed memoryQueryFailed ->
          handleMemoryQueryFailed(memoryQueryFailed);
      case Message.TriggerContextMerge trigger -> handleTriggerContextMerge(trigger);
      case Message.TriggerMemoryUpdate update -> handleTriggerMemoryUpdate(update);
      case Message.ProcessNextCandidate next -> handleProcessNextCandidate(next);
      case Message.MemoryUpdateFailed failed ->
          logger.warn("Memory update failed for prompt: {} ({})", failed.prompt(), failed.reason());
    }
  }

  private void handleProcess(final @NotNull Message.Process cmd) {
    final ActorHandle<PromptResponse> replyTo = sender();

    // Spawn a bridge child actor that receives the PromptResponse from the LLM pipeline
    // and forwards it back to this orchestrator as a PipelineDone message.
    final var self = self();
    final var prompt = cmd.text();

    final var bridge = context().spawn("pipeline-bridge-" + bridgeCounter.getAndIncrement(),
        () -> new Actor<PromptResponse>() {
          @Override
          public void onMessage(final @NotNull PromptResponse response) {
            send((Message) new Message.PipelineDone(prompt, response, replyTo)).to(self);
          }
        });

    final RichFuture<Message> phase = embeddingPort.embed(prompt).thenApply(
            embedding -> (Message) new Message.EmbeddingReady(prompt, embedding, replyTo, bridge))
        .exceptionally(e -> new Message.EmbeddingFailed(prompt, e, replyTo));

    pipeFuture(phase).to(self());
  }

  private void handleEmbeddingReady(final @NotNull Message.EmbeddingReady embeddingReady) {
    final RichFuture<Message> queryFlow = asRichFuture(context().requestResponseTo(
                (MemoryCommand) new MemoryCommand.Query(embeddingReady.embedding(), maxMemoryResults))
            .ofType(MemoryResponse.class).from(memoryActor))
        .thenCompose(this::queryEntriesFromResponse)
        .thenApply(memories ->
            (Message) new Message.MemoryQueryReady(embeddingReady.prompt(), memories,
                embeddingReady.replyTo(), embeddingReady.pipelineReplyTo()))
        .exceptionally(
            e -> new Message.MemoryQueryFailed(embeddingReady.prompt(), e, embeddingReady.replyTo()));

    pipeFuture(queryFlow).to(self());
  }

  private void handleEmbeddingFailed(final @NotNull Message.EmbeddingFailed embeddingFailed) {
    send((Message) new Message.PipelineDone(embeddingFailed.prompt(),
        new PromptResponse.Failure(
            "Error during embedding: %s".formatted(embeddingFailed.cause().getMessage()),
            embeddingFailed.cause()),
        embeddingFailed.replyTo())).to(self());
  }

  private void handleMemoryQueryReady(final @NotNull Message.MemoryQueryReady memoryQueryReady) {
    send((Message) new Message.TriggerContextMerge(memoryQueryReady.prompt(),
        memoryQueryReady.memories(), memoryQueryReady.pipelineReplyTo())).to(self());
  }

  private void handleMemoryQueryFailed(final @NotNull Message.MemoryQueryFailed memoryQueryFailed) {
    send((Message) new Message.PipelineDone(memoryQueryFailed.prompt(),
        new PromptResponse.Failure(
            "Error during memory query: %s".formatted(memoryQueryFailed.cause().getMessage()),
            memoryQueryFailed.cause()),
        memoryQueryFailed.replyTo())).to(self());
  }

  private void handleTriggerContextMerge(final @NotNull Message.TriggerContextMerge trigger) {
    send((ContextMergeActor.Message) new ContextMergeActor.Message.Merge(trigger.prompt(),
        trigger.memories(), llmCallActor, trigger.replyTo())).to(contextMergeActor);
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
    final var content = "%s:%s".formatted(candidate.subject(), candidate.fact());

    // Delegate all memorization details (including embedding) to the MemorizationStrategy
    // inside MemoryActor — callers only supply raw text.
    final RichFuture<Message> writeFlow = asRichFuture(
            context().requestResponseTo((MemoryCommand) new MemoryCommand.Memorize(content, Map.of()))
                .ofType(MemoryResponse.class).from(memoryActor))
        .thenCompose(this::writeResultFromResponse)
        .thenApply(
        unused -> (Message) new Message.ProcessNextCandidate(next.prompt(), next.candidates(),
            next.index() + 1)).exceptionally(
        e -> new Message.MemoryUpdateFailed(next.prompt(), Objects.toString(e.getMessage())));

    pipeFuture(writeFlow).to(self());
  }

  private @NotNull RichFuture<List<MemoryEntry>> queryEntriesFromResponse(
      final @NotNull MemoryResponse response) {

    if (response instanceof MemoryResponse.QueryResult(List<MemoryEntry> entries)) {
      return RichFuture.of(CompletableFuture.completedFuture(entries));
    } else if (response instanceof MemoryResponse.Failure(String errorMessage)) {
      return RichFuture.of(
          CompletableFuture.failedFuture(new IllegalStateException(errorMessage)));
    }

    return RichFuture.of(CompletableFuture.failedFuture(new IllegalStateException(
        "Unexpected response: %s".formatted(response.getClass().getName()))));
  }

  private @NotNull RichFuture<Void> writeResultFromResponse(final @NotNull MemoryResponse response) {

    if (response instanceof MemoryResponse.Written) {
      return RichFuture.of(CompletableFuture.completedFuture(null));
    } else if (response instanceof MemoryResponse.Failure(String errorMessage)) {
      return RichFuture.of(
          CompletableFuture.failedFuture(new IllegalStateException(errorMessage)));
    }

    return RichFuture.of(CompletableFuture.failedFuture(new IllegalStateException(
        "Unexpected response: %s".formatted(response.getClass().getName()))));
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
