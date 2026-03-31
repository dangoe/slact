package de.dangoe.concurrent.slact.memory.mcp;

import de.dangoe.concurrent.slact.memory.Embedding;
import de.dangoe.concurrent.slact.memory.MemoryEntry;
import de.dangoe.concurrent.slact.memory.MemoryQuery;
import de.dangoe.concurrent.slact.memory.MemoryStore;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP server that exposes memory read and write operations as tools.
 *
 * <p>Wraps a {@link MemoryStore} and exposes two tools:
 * <ul>
 *   <li>{@code write_memory} — stores text content with its pre-computed embedding.</li>
 *   <li>{@code query_memory} — retrieves similar memories for a given embedding.</li>
 * </ul>
 */
public final class MemoryMcpServer {

  private static final Logger logger = LoggerFactory.getLogger(MemoryMcpServer.class);

  private static final McpSchema.JsonSchema WRITE_MEMORY_SCHEMA = new McpSchema.JsonSchema(
      "object",
      Map.of(
          "content", Map.of("type", "string", "description", "Text content to remember"),
          "embedding", Map.of("type", "array", "items", Map.of("type", "number"),
              "description", "Pre-computed embedding vector")),
      List.of("content", "embedding"),
      null, null, null);

  private static final McpSchema.JsonSchema QUERY_MEMORY_SCHEMA = new McpSchema.JsonSchema(
      "object",
      Map.of(
          "embedding", Map.of("type", "array", "items", Map.of("type", "number"),
              "description", "Query embedding vector"),
          "maxResults", Map.of("type", "integer",
              "description", "Maximum number of results to return")),
      List.of("embedding", "maxResults"),
      null, null, null);

  private final @NotNull MemoryStore store;

  /**
   * Creates a new {@link MemoryMcpServer}.
   *
   * @param store the memory store to delegate operations to.
   */
  public MemoryMcpServer(final @NotNull MemoryStore store) {
    this.store = Objects.requireNonNull(store, "Memory store must not be null");
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
                .name("write_memory")
                .description("Stores a memory entry with its pre-computed embedding vector")
                .inputSchema(WRITE_MEMORY_SCHEMA)
                .build(),
            this::handleWriteMemory)
        .toolCall(
            McpSchema.Tool.builder()
                .name("query_memory")
                .description("Queries similar memories for a given embedding vector")
                .inputSchema(QUERY_MEMORY_SCHEMA)
                .build(),
            this::handleQueryMemory)
        .build();

    logger.info("MemoryMcpServer started.");
  }

  private @NotNull CallToolResult handleWriteMemory(
      final @NotNull McpSyncServerExchange exchange,
      final @NotNull McpSchema.CallToolRequest request) {
    try {
      final var args = request.arguments();
      final var content = (String) args.get("content");
      final var embedding = toFloatArray(args.get("embedding"));

      final var memory = de.dangoe.concurrent.slact.memory.Memory.of(
          content, embedding, Map.of());
      final var id = store.save(memory).join();

      return CallToolResult.builder()
          .addTextContent("Memory stored with id: " + id)
          .build();
    } catch (final Exception e) {
      logger.error("Failed to write memory", e);
      return CallToolResult.builder()
          .addTextContent("Error: " + e.getMessage())
          .isError(true)
          .build();
    }
  }

  private @NotNull CallToolResult handleQueryMemory(
      final @NotNull McpSyncServerExchange exchange,
      final @NotNull McpSchema.CallToolRequest request) {
    try {
      final var args = request.arguments();
      final var embedding = toFloatArray(args.get("embedding"));
      final var maxResults = ((Number) args.get("maxResults")).intValue();

      final var results = store.query(
          new MemoryQuery(new Embedding(embedding), maxResults)).join();

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

  private static float @NotNull [] toFloatArray(final @NotNull Object raw) {
    @SuppressWarnings("unchecked") final var list = (List<Number>) raw;
    final float[] array = new float[list.size()];
    for (int i = 0; i < list.size(); i++) {
      array[i] = list.get(i).floatValue();
    }
    return array;
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
}
