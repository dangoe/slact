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
import org.jetbrains.annotations.Nullable;
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
      Message.TriggerContextMerge, Message.TriggerMemoryUpdate, Message.ProcessNextCandidate,
      Message.CandidateEmbeddingDone, Message.MemoryUpdateFailed {

    record Process(@NotNull String text) implements Message {}

    record PipelineDone(@NotNull String prompt, @NotNull PromptResponse result,
                        @NotNull ActorHandle<PromptResponse> replyTo) implements Message {}

    record TriggerContextMerge(@NotNull String prompt, @NotNull List<MemoryEntry> memories,
                               @NotNull ActorHandle<PromptResponse> replyTo) implements Message {}

    record TriggerMemoryUpdate(@NotNull String prompt, @NotNull String response)
        implements Message {}

    record ProcessNextCandidate(@NotNull String prompt, @NotNull List<MemoryCandidate> candidates,
                                int index) implements Message {}

    record CandidateEmbeddingDone(@NotNull String prompt,
                                  @NotNull List<MemoryCandidate> candidates, int index,
                                  @NotNull String content,
                                  @NotNull Embedding embedding) implements Message {}

    record MemoryUpdateFailed(@NotNull String prompt, @NotNull String reason)
        implements Message {}
  }

  private static final int DEFAULT_MAX_MEMORY_RESULTS = 5;

  private final @NotNull EmbeddingPort embeddingPort;
  private final @NotNull ActorHandle<MemoryCommand> memoryActor;
  private final int maxMemoryResults;
  private final @NotNull ActorHandle<ContextMergeActor.Message> contextMergeActor;
  private final @NotNull ActorHandle<LlmCallActor.Message> llmCallActor;
  private final @NotNull MemoryExtractionPort memoryExtractionPort;

  /** Temporary child actor that bridges PromptResponse back to this actor as PipelineDone. */
  private @Nullable ActorHandle<PromptResponse> activeBridge = null;

  public PromptOrchestratorActor(
      final @NotNull EmbeddingPort embeddingPort,
      final @NotNull ActorHandle<MemoryCommand> memoryActor,
      final int maxMemoryResults,
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

  public PromptOrchestratorActor(
      final @NotNull EmbeddingPort embeddingPort,
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
      case Message.TriggerContextMerge trigger -> handleTriggerContextMerge(trigger);
      case Message.TriggerMemoryUpdate update -> handleTriggerMemoryUpdate(update);
      case Message.ProcessNextCandidate next -> handleProcessNextCandidate(next);
      case Message.CandidateEmbeddingDone embedded -> handleCandidateEmbeddingDone(embedded);
      case Message.MemoryUpdateFailed failed ->
          logger.warn("Memory update failed for prompt: {} ({})", failed.prompt(), failed.reason());
    }
  }

  private void handleProcess(final @NotNull Message.Process cmd) {
    final ActorHandle<PromptResponse> replyTo = sender();

    // Spawn a bridge child actor that will receive the PromptResponse from the LLM pipeline
    // and forward it back to this orchestrator as a PipelineDone message.
    final var self = self();
    final var prompt = cmd.text();
    activeBridge = context().spawn(
        "pipeline-bridge-" + bridgeCounter.getAndIncrement(),
        () -> new Actor<PromptResponse>() {
          @Override
          public void onMessage(final @NotNull PromptResponse response) {
            send((Message) new Message.PipelineDone(prompt, response, replyTo)).to(self);
          }
        });

    // Embed + query memory, then pipe TriggerContextMerge to self
    final var bridge = activeBridge;
    final RichFuture<Message> phase = embeddingPort.embed(prompt)
        .thenCompose(embedding -> askMemoryQuery(embedding, memoryActor).thenApply(
            memories -> (Message) new Message.TriggerContextMerge(prompt, memories, bridge)))
        .exceptionally(
            e -> new Message.PipelineDone(prompt, new PromptResponse.Failure(e.getMessage()),
                replyTo));
    pipeFuture(phase).to(self());
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
            update.response())
        .thenApply(candidates -> (Message) new Message.ProcessNextCandidate(update.prompt(),
            candidates, 0))
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

  private @NotNull RichFuture<List<MemoryEntry>> askMemoryQuery(
      final @NotNull Embedding embedding,
      final @NotNull ActorHandle<MemoryCommand> actor) {
    return asRichFuture(
        context().requestResponseTo(
                (MemoryCommand) new MemoryCommand.Query(embedding, maxMemoryResults))
            .ofType(MemoryResponse.class).from(actor)).thenCompose(response -> {
      if (response instanceof MemoryResponse.QueryResult(List<MemoryEntry> entries)) {
        return RichFuture.of(CompletableFuture.completedFuture(entries));
      }
      if (response instanceof MemoryResponse.Failure(String errorMessage)) {
        return RichFuture.of(
            CompletableFuture.failedFuture(new IllegalStateException(errorMessage)));
      }
      return RichFuture.of(CompletableFuture.failedFuture(
          new IllegalStateException("Unexpected response: " + response.getClass().getName())));
    });
  }

  private @NotNull RichFuture<Void> askMemoryWrite(final @NotNull String content,
      final @NotNull Embedding embedding) {
    return asRichFuture(
        context().requestResponseTo(
                (MemoryCommand) new MemoryCommand.Memorize(content, embedding, Map.of()))
            .ofType(MemoryResponse.class).from(memoryActor)).thenCompose(response -> {
      if (response instanceof MemoryResponse.Written) {
        return RichFuture.of(CompletableFuture.completedFuture(null));
      }
      if (response instanceof MemoryResponse.Failure(String errorMessage)) {
        return RichFuture.of(
            CompletableFuture.failedFuture(new IllegalStateException(errorMessage)));
      }
      return RichFuture.of(CompletableFuture.failedFuture(
          new IllegalStateException("Unexpected response: " + response.getClass().getName())));
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
