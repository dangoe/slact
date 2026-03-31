package de.dangoe.concurrent.slact.memory.demo;

import de.dangoe.concurrent.slact.core.SlactContainerBuilder;
import de.dangoe.concurrent.slact.memory.MemoryCommand;
import de.dangoe.concurrent.slact.memory.MemoryWriteActor;
import de.dangoe.concurrent.slact.memory.PromptOrchestratorActor;
import de.dangoe.concurrent.slact.memory.PromptResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
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

  private MemoryDemoCli() {
    // utility class
  }

  /**
   * Application entry point.
   *
   * @param args command-line arguments (unused).
   * @throws Exception if startup or shutdown fails.
   */
  public static void main(final String[] args) throws Exception {
    final var embeddingPort = new StubEmbeddingPort();
    final var targetModelPort = new StubTargetModelPort();
    final var extractionPort = new StubMemoryExtractionPort();
    final var memoryStore = new InMemoryMemoryStore();

    try (final var container = new SlactContainerBuilder().build()) {
      final var writeActor = container.spawn(
          "write-actor",
          () -> new MemoryWriteActor(memoryStore));
      final var orchestrator = container.spawn(
          "orchestrator",
          () -> new PromptOrchestratorActor(
              embeddingPort, memoryStore, targetModelPort, extractionPort, writeActor));

      logger.info("Memory demo started. Enter prompts (Ctrl+D to exit):");

      final var reader = new BufferedReader(new InputStreamReader(System.in));
      String line;
      while ((line = reader.readLine()) != null) {
        final var trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        try {
          final var response = container.requestResponseTo(
                  (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process(trimmed))
              .ofType(PromptResponse.class)
              .from(orchestrator);

          final var result = response.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
          switch (result) {
            case PromptResponse.Answer answer ->
                System.out.println("Answer: " + answer.text()); // NOSONAR: intentional CLI output
            case PromptResponse.Failure failure ->
                System.out.println("Error: " + failure.errorMessage()); // NOSONAR: intentional CLI output
          }
        } catch (final Exception e) {
          logger.error("Failed to process prompt: {}", trimmed, e);
        }
      }
    }
  }
}
