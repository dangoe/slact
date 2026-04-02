package de.dangoe.concurrent.slact.ai.memory.demo;

import de.dangoe.concurrent.slact.ai.memory.ContextMergeActor;
import de.dangoe.concurrent.slact.ai.memory.EmbeddingBasedMemoryStrategy;
import de.dangoe.concurrent.slact.ai.memory.LlmCallActor;
import de.dangoe.concurrent.slact.ai.memory.MemoryActor;
import de.dangoe.concurrent.slact.ai.memory.PromptOrchestratorActor;
import de.dangoe.concurrent.slact.ai.memory.PromptResponse;
import de.dangoe.concurrent.slact.ai.memory.neo4j.Neo4jMemoryStore;
import de.dangoe.concurrent.slact.ai.memory.ollama.OllamaEmbeddingAdapter;
import de.dangoe.concurrent.slact.core.SlactContainerBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI entry point that demonstrates the prompt orchestrator pipeline backed by Neo4j and Ollama.
 *
 * <p>Reads prompts from stdin line-by-line, sends each to a {@link PromptOrchestratorActor},
 * and prints the response to stdout.
 *
 * <p>Configuration is provided via environment variables:
 * <ul>
 *   <li>{@code NEO4J_URI} — Neo4j bolt URI (default: {@code bolt://localhost:7687})</li>
 *   <li>{@code NEO4J_USER} — Neo4j username (default: {@code neo4j})</li>
 *   <li>{@code NEO4J_PASSWORD} — Neo4j password (default: {@code password})</li>
 *   <li>{@code NEO4J_DATABASE} — Neo4j database name (default: {@code neo4j})</li>
 *   <li>{@code OLLAMA_URL} — Ollama base URL (default: {@code http://localhost:11434})</li>
 *   <li>{@code OLLAMA_MODEL} — Ollama embedding model (default: {@code nomic-embed-text})</li>
 *   <li>{@code EMBEDDING_DIMENSION} — Embedding vector dimension (default: {@code 768})</li>
 * </ul>
 *
 * <p>Start the required infrastructure with:
 * <pre>
 *   docker compose up -d
 *   docker exec -it &lt;ollama-container&gt; ollama pull nomic-embed-text
 * </pre>
 */
public final class MemoryDemoCli {

  private static final Logger logger = LoggerFactory.getLogger(MemoryDemoCli.class);
  private static final long RESPONSE_TIMEOUT_SECONDS = 30L;
  private static final String EXIT_COMMAND = "exit";
  private static final String QUIT_COMMAND = "quit";

  private static final Map<String, String> ENV = System.getenv();

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
    final var neo4jUri = ENV.getOrDefault("NEO4J_URI", "bolt://localhost:7687");
    final var neo4jUser = ENV.getOrDefault("NEO4J_USER", "neo4j");
    final var neo4jPassword = ENV.getOrDefault("NEO4J_PASSWORD", "password");
    final var neo4jDatabase = ENV.getOrDefault("NEO4J_DATABASE", "neo4j");
    final var ollamaUrl = ENV.getOrDefault("OLLAMA_URL", "http://localhost:11434");
    final var ollamaModel = ENV.getOrDefault("OLLAMA_MODEL", "nomic-embed-text");
    final var embeddingDimension = Integer.parseInt(ENV.getOrDefault("EMBEDDING_DIMENSION", "768"));

    final var embeddingAdapter = new OllamaEmbeddingAdapter(ollamaUrl, ollamaModel);
    final var targetModelAdapter = new StubTargetModelAdapter();
    final var extractionAdapter = new StubMemoryExtractionAdapter();

    final var driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPassword));
    final var memoryStore = new Neo4jMemoryStore(driver, neo4jDatabase, embeddingDimension, 0.95);
    memoryStore.initialize();

    try (driver; final var container = new SlactContainerBuilder().build()) {
      final var memoryStrategy = new EmbeddingBasedMemoryStrategy(embeddingAdapter, memoryStore);
      final var memoryActor = container.spawn("memory-actor",
          () -> new MemoryActor(memoryStrategy));
      final var contextMergeActor = container.spawn("context-merge-actor", ContextMergeActor::new);
      final var llmCallActor = container.spawn("llm-call-actor",
          () -> new LlmCallActor(targetModelAdapter));
      final var orchestrator = container.spawn("orchestrator",
          () -> new PromptOrchestratorActor(memoryActor, contextMergeActor,
              llmCallActor, extractionAdapter));

      logger.info("Memory demo started (Neo4j: {}, Ollama: {} / {}).", neo4jUri, ollamaUrl,
          ollamaModel);
      logger.info("Enter prompts and press Enter. Type '{}' or '{}' to stop (Ctrl+D also exits).",
          EXIT_COMMAND, QUIT_COMMAND);

      final var reader = new BufferedReader(new InputStreamReader(System.in));
      runCliLoop(reader, System.out::println, prompt -> {
        final var response = container.requestResponseTo(
                (PromptOrchestratorActor.Message) new PromptOrchestratorActor.Message.Process(prompt))
            .ofType(PromptResponse.class).from(orchestrator);
        return response.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      });
    } catch (final Exception e) {
      logger.error("An error occurred during the memory demo.", e);
      throw e;
    }
  }

  static void runCliLoop(final @NotNull BufferedReader reader,
      final @NotNull Consumer<String> output, final @NotNull PromptProcessor promptProcessor)
      throws Exception {
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
        case PromptResponse.Failure failure -> {
          output.accept("Error: " + failure.errorMessage());
          logger.error("Prompt processing failed for input: {}", trimmed, failure.cause());
        }
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

