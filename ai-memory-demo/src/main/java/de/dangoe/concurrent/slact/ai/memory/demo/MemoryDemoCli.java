package de.dangoe.concurrent.slact.ai.memory.demo;

import de.dangoe.concurrent.slact.ai.memory.ContextMergeActor;
import de.dangoe.concurrent.slact.ai.memory.LlmCallActor;
import de.dangoe.concurrent.slact.ai.memory.MemoryActor;
import de.dangoe.concurrent.slact.ai.memory.PromptOrchestratorActor;
import de.dangoe.concurrent.slact.ai.memory.PromptResponse;
import de.dangoe.concurrent.slact.core.SlactContainerBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI entry point that demonstrates the prompt orchestrator pipeline.
 *
 * <p>Reads prompts from stdin line-by-line, sends each to a {@link PromptOrchestratorActor},
 * and prints the response to stdout. Uses stub ports and an in-memory store by default.
 */
public final class MemoryDemoCli {

  private static final Logger logger = LoggerFactory.getLogger(MemoryDemoCli.class);
  private static final long RESPONSE_TIMEOUT_SECONDS = 30L;
  private static final String EXIT_COMMAND = "exit";
  private static final String QUIT_COMMAND = "quit";

  private MemoryDemoCli() {
    // utility class
  }

  /**
   * Application entry point.
   *
   * @param args command-line arguments (unused).
   * @throws Exception if startup or shutdown fails.
   */
  public static void main(final @NotNull String[] args) throws Exception {
    final var embeddingPort = new StubEmbeddingAdapter();
    final var targetModelPort = new StubTargetModelAdapter();
    final var extractionPort = new StubMemoryExtractionAdapter();
    final var memoryStore = new InMemoryMemoryStore();

    try (final var container = new SlactContainerBuilder().build()) {
      final var memoryActor = container.spawn(
          "memory-actor",
          () -> new MemoryActor(memoryStore));
      final var contextMergeActor = container.spawn(
          "context-merge-actor",
          ContextMergeActor::new);
      final var llmCallActor = container.spawn(
          "llm-call-actor",
          () -> new LlmCallActor(targetModelPort));
      final var orchestrator = container.spawn(
          "orchestrator",
          () -> new PromptOrchestratorActor(
              embeddingPort, memoryActor, contextMergeActor, llmCallActor, extractionPort));

      logger.info("Memory demo started.");
      logger.info(
          "Enter prompts and press Enter. Type '{}' or '{}' to stop (Ctrl+D also exits).",
          EXIT_COMMAND, QUIT_COMMAND);

      final var reader = new BufferedReader(new InputStreamReader(System.in));
      runCliLoop(reader, line -> System.out.println(line),
          prompt -> { // NOSONAR: intentional CLI output
            final var response = container.requestResponseTo(
                    (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process(
                        prompt))
                .ofType(PromptResponse.class)
                .from(orchestrator);
            return response.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
          });
    }
  }

  static void runCliLoop(
      final @NotNull BufferedReader reader,
      final @NotNull Consumer<String> output,
      final @NotNull PromptProcessor promptProcessor) throws Exception {
    String line;
    while ((line = reader.readLine()) != null) {
      final var trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (isShutdownCommand(trimmed)) {
        logger.info("Shutdown command received. Exiting memory demo.");
        return;
      }
      final var result = promptProcessor.process(trimmed);
      switch (result) {
        case PromptResponse.Answer answer -> output.accept("Answer: " + answer.text());
        case PromptResponse.Failure failure -> output.accept("Error: " + failure.errorMessage());
      }
    }
    logger.info("Input closed. Exiting memory demo.");
  }

  static boolean isShutdownCommand(final @NotNull String input) {
    return EXIT_COMMAND.equalsIgnoreCase(input) || QUIT_COMMAND.equalsIgnoreCase(input);
  }

  @FunctionalInterface
  interface PromptProcessor {

    @NotNull PromptResponse process(@NotNull String prompt) throws Exception;
  }
}
