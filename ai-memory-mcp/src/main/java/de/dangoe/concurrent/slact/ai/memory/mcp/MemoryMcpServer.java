package de.dangoe.concurrent.slact.ai.memory.mcp;

import de.dangoe.concurrent.slact.ai.memory.Embedding;
import de.dangoe.concurrent.slact.ai.memory.EmbeddingPort;
import de.dangoe.concurrent.slact.ai.memory.MemoryEntry;
import de.dangoe.concurrent.slact.ai.memory.MemoryQuery;
import de.dangoe.concurrent.slact.ai.memory.MemoryStore;
import de.dangoe.concurrent.slact.ai.memory.PromptResponse;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP server that exposes memory query, deletion, and prompt orchestration as tools.
 *
 * <p>Provides three tools:
 * <ul>
 *   <li>{@code query_memory} — retrieves similar memories for a given text criteria. The criteria
 *       is embedded internally via the {@link EmbeddingPort}.</li>
 *   <li>{@code delete_memory} — deletes a stored memory entry by its ID.</li>
 *   <li>{@code process_prompt} — sends a prompt through the full orchestration pipeline and
 *       returns the answer.</li>
 * </ul>
 */
public final class MemoryMcpServer {

  private static final String TOOL_QUERY_MEMORY = "query_memory";
  private static final String TOOL_DELETE_MEMORY = "delete_memory";
  private static final String TOOL_PROCESS_PROMPT = "process_prompt";
  private static final int DEFAULT_MAX_RESULTS = 5;

  private static final Logger logger = LoggerFactory.getLogger(MemoryMcpServer.class);

  private static final McpSchema.JsonSchema QUERY_MEMORY_SCHEMA = new McpSchema.JsonSchema(
      "object",
      Map.of(
          "criteria", Map.of("type", "string",
              "description", "Text criteria for finding similar memories"),
          "maxResults", Map.of("type", "integer",
              "description", "Maximum number of results to return (default: "
                  + DEFAULT_MAX_RESULTS + ")")),
      List.of("criteria"),
      null, null, null);

  private static final McpSchema.JsonSchema PROCESS_PROMPT_SCHEMA = new McpSchema.JsonSchema(
      "object",
      Map.of(
          "prompt", Map.of("type", "string",
              "description", "The prompt to process through the AI memory pipeline")),
      List.of("prompt"),
      null, null, null);

  private static final McpSchema.JsonSchema DELETE_MEMORY_SCHEMA = new McpSchema.JsonSchema(
      "object",
      Map.of(
          "id", Map.of("type", "string",
              "description", "UUID of the memory entry to delete")),
      List.of("id"),
      null, null, null);

  private final @NotNull EmbeddingPort embeddingPort;
  private final @NotNull MemoryStore memoryStore;
  private final @NotNull PromptHandler promptHandler;

  /**
   * Creates a new {@link MemoryMcpServer}.
   *
   * @param embeddingPort the port used to embed query criteria; must not be {@code null}.
   * @param memoryStore   the memory store used for similarity queries; must not be {@code null}.
   * @param promptHandler the handler that processes prompts via the orchestration pipeline; must
   *                      not be {@code null}.
   */
  public MemoryMcpServer(
      final @NotNull EmbeddingPort embeddingPort,
      final @NotNull MemoryStore memoryStore,
      final @NotNull PromptHandler promptHandler) {
    this.embeddingPort = Objects.requireNonNull(embeddingPort, "EmbeddingPort must not be null");
    this.memoryStore = Objects.requireNonNull(memoryStore, "Memory store must not be null");
    this.promptHandler = Objects.requireNonNull(promptHandler, "PromptHandler must not be null");
  }

  /**
   * Starts the MCP server using stdio transport and blocks until the server closes.
   */
  public void start() {
    final var jsonMapper = McpJsonDefaults.getMapper();
    final var transportProvider = new StdioServerTransportProvider(jsonMapper);

    McpServer.sync(transportProvider)
        .serverInfo("memory-mcp", "1.0.0")
        .toolCall(
            McpSchema.Tool.builder()
                .name(TOOL_QUERY_MEMORY)
                .description(
                    "Queries memories similar to the given text criteria. "
                        + "The criteria is embedded internally.")
                .inputSchema(QUERY_MEMORY_SCHEMA)
                .build(),
            this::handleQueryMemory)
        .toolCall(
            McpSchema.Tool.builder()
                .name(TOOL_DELETE_MEMORY)
                .description("Deletes a stored memory entry by its UUID.")
                .inputSchema(DELETE_MEMORY_SCHEMA)
                .build(),
            this::handleDeleteMemory)
        .toolCall(
            McpSchema.Tool.builder()
                .name(TOOL_PROCESS_PROMPT)
                .description(
                    "Sends a prompt through the full AI memory pipeline and returns the answer. "
                        + "Relevant memories are retrieved and injected into the context "
                        + "automatically.")
                .inputSchema(PROCESS_PROMPT_SCHEMA)
                .build(),
            this::handleProcessPrompt)
        .build();

    logger.info("MemoryMcpServer started.");
  }

  @NotNull CallToolResult handleQueryMemory(
      final @NotNull McpSyncServerExchange exchange,
      final @NotNull McpSchema.CallToolRequest request) {
    try {
      final var args = request.arguments();
      final var criteria = (String) args.get("criteria");
      final var maxResults = args.containsKey("maxResults")
          ? ((Number) args.get("maxResults")).intValue()
          : DEFAULT_MAX_RESULTS;

      final Embedding embedding = embeddingPort.embed(criteria).join();
      final var results = memoryStore.query(new MemoryQuery(embedding, maxResults)).join();

      return CallToolResult.builder()
          .addTextContent(formatResults(results))
          .build();
    } catch (final Exception e) {
      logger.error("Failed to query memory", e);
      return CallToolResult.builder()
          .addTextContent("Error: " + e.getMessage())
          .isError(true)
          .build();
    }
  }

  @NotNull CallToolResult handleDeleteMemory(
      final @NotNull McpSyncServerExchange exchange,
      final @NotNull McpSchema.CallToolRequest request) {
    try {
      final var args = request.arguments();
      final var id = UUID.fromString((String) args.get("id"));

      memoryStore.delete(id).join();

      return CallToolResult.builder()
          .addTextContent("Memory " + id + " deleted.")
          .build();
    } catch (final IllegalArgumentException e) {
      logger.error("Invalid memory ID format", e);
      return CallToolResult.builder()
          .addTextContent("Error: invalid UUID format — " + e.getMessage())
          .isError(true)
          .build();
    } catch (final Exception e) {
      logger.error("Failed to delete memory", e);
      return CallToolResult.builder()
          .addTextContent("Error: " + e.getMessage())
          .isError(true)
          .build();
    }
  }

  @NotNull CallToolResult handleProcessPrompt(
      final @NotNull McpSyncServerExchange exchange,
      final @NotNull McpSchema.CallToolRequest request) {
    try {
      final var args = request.arguments();
      final var prompt = (String) args.get("prompt");

      final var response = promptHandler.process(prompt);
      return switch (response) {
        case PromptResponse.Answer answer -> CallToolResult.builder()
            .addTextContent(answer.text())
            .build();
        case PromptResponse.Failure failure -> CallToolResult.builder()
            .addTextContent("Error: " + failure.errorMessage())
            .isError(true)
            .build();
      };
    } catch (final Exception e) {
      logger.error("Failed to process prompt", e);
      return CallToolResult.builder()
          .addTextContent("Error: " + e.getMessage())
          .isError(true)
          .build();
    }
  }

  private static @NotNull String formatResults(final @NotNull List<MemoryEntry> entries) {
    if (entries.isEmpty()) {
      return "No matching memories found.";
    }
    final var sb = new StringBuilder();
    for (final var entry : entries) {
      sb.append("- [score=").append(entry.score().value()).append("] ")
          .append(entry.memory().content()).append('\n');
    }
    return sb.toString();
  }

  /**
   * Handles processing of a prompt via the orchestration pipeline.
   */
  @FunctionalInterface
  public interface PromptHandler {

    /**
     * Processes the given prompt and returns the result.
     *
     * @param prompt the prompt text.
     * @return the response from the pipeline.
     * @throws Exception if processing fails.
     */
    @NotNull PromptResponse process(@NotNull String prompt) throws Exception;
  }
}
