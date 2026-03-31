package de.dangoe.concurrent.slact.memory;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PromptOrchestratorActor extends Actor<PromptOrchestratorActor.Message> {

  private static final Logger logger = LoggerFactory.getLogger(PromptOrchestratorActor.class);

  public sealed interface Message permits Message.Process, Message.PipelineDone {
    record Process(@NotNull String text) implements Message {}
    record PipelineDone(
        @NotNull PromptResponse result,
        @NotNull ActorHandle<PromptResponse> replyTo) implements Message {}
  }

  private static final int DEFAULT_MAX_MEMORY_RESULTS = 5;

  private final @NotNull EmbeddingPort embeddingPort;
  private final @NotNull MemoryStore memoryStore;
  private final int maxMemoryResults;
  private final @NotNull TargetModelPort targetModelPort;
  private final @NotNull MemoryExtractionPort memoryExtractionPort;
  private final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor;

  public PromptOrchestratorActor(
      final @NotNull EmbeddingPort embeddingPort,
      final @NotNull MemoryStore memoryStore,
      final int maxMemoryResults,
      final @NotNull TargetModelPort targetModelPort,
      final @NotNull MemoryExtractionPort memoryExtractionPort,
      final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor) {
    this.embeddingPort = Objects.requireNonNull(embeddingPort, "EmbeddingPort must not be null");
    this.memoryStore = Objects.requireNonNull(memoryStore, "MemoryStore must not be null");
    this.maxMemoryResults = maxMemoryResults;
    this.targetModelPort = Objects.requireNonNull(targetModelPort, "TargetModelPort must not be null");
    this.memoryExtractionPort = Objects.requireNonNull(memoryExtractionPort, "MemoryExtractionPort must not be null");
    this.memoryWriteActor = Objects.requireNonNull(memoryWriteActor, "MemoryWriteActor must not be null");
  }

  public PromptOrchestratorActor(
      final @NotNull EmbeddingPort embeddingPort,
      final @NotNull MemoryStore memoryStore,
      final @NotNull TargetModelPort targetModelPort,
      final @NotNull MemoryExtractionPort memoryExtractionPort,
      final @NotNull ActorHandle<MemoryCommand.WriteMemory> memoryWriteActor) {
    this(embeddingPort, memoryStore, DEFAULT_MAX_MEMORY_RESULTS, targetModelPort, memoryExtractionPort, memoryWriteActor);
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
    final RichFuture<Message> pipeline = buildPipeline(cmd.text())
        .thenApply(result -> (Message) new Message.PipelineDone(result, replyTo));
    pipeFuture(pipeline).to(self());
  }

  private @NotNull RichFuture<PromptResponse> buildPipeline(final @NotNull String promptText) {
    return embeddingPort.embed(promptText)
        .thenCompose(embedding ->
            memoryStore.query(new MemoryQuery(embedding, maxMemoryResults))
                .thenCompose(memories -> {
                  final var enrichedPrompt = mergeContext(promptText, memories);
                  return targetModelPort.complete(enrichedPrompt)
                      .thenApply(response -> {
                        extractAndSaveMemoriesAsync(promptText, response);
                        return (PromptResponse) new PromptResponse.Answer(response);
                      });
                })
        )
        .exceptionally(e -> new PromptResponse.Failure(e.getMessage()));
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

  private void extractAndSaveMemoriesAsync(
      final @NotNull String prompt,
      final @NotNull String response) {
    Thread.startVirtualThread(() -> {
      try {
        final var candidates = memoryExtractionPort.extract(prompt, response).join();
        for (final var candidate : candidates) {
          final var content = candidate.subject() + ": " + candidate.fact();
          final var embedding = embeddingPort.embed(content).join();
          memoryWriteActor.send(new MemoryCommand.WriteMemory(content, embedding, Map.of()));
        }
      } catch (final Exception e) {
        logger.warn("Memory extraction failed for prompt: {}", prompt, e);
      }
    });
  }
}
