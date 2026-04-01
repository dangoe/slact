package de.dangoe.concurrent.slact.ai.memory.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dangoe.concurrent.slact.ai.memory.Embedding;
import de.dangoe.concurrent.slact.ai.memory.EmbeddingPort;
import de.dangoe.concurrent.slact.ai.memory.Memory;
import de.dangoe.concurrent.slact.ai.memory.MemoryEntry;
import de.dangoe.concurrent.slact.ai.memory.MemoryStore;
import de.dangoe.concurrent.slact.ai.memory.PromptResponse;
import de.dangoe.concurrent.slact.ai.memory.Score;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryMcpServer")
class MemoryMcpServerTest {

  private static final Embedding EMBEDDING = new Embedding(new float[]{0.1f, 0.2f, 0.3f});

  private EmbeddingPort embeddingPort;
  private MemoryStore memoryStore;
  private MemoryMcpServer.PromptHandler promptHandler;
  private MemoryMcpServer server;

  @BeforeEach
  void setUp() {
    embeddingPort = mock(EmbeddingPort.class);
    memoryStore = mock(MemoryStore.class);
    promptHandler = mock(MemoryMcpServer.PromptHandler.class);
    server = new MemoryMcpServer(embeddingPort, memoryStore, promptHandler);
  }

  @Nested
  @DisplayName("given a query_memory call")
  class GivenAQueryMemoryCall {

    @Test
    @DisplayName("should return formatted memory entries when matches are found")
    void shouldReturnFormattedMemoryEntriesWhenMatchesAreFound() throws Exception {
      final var memory = Memory.of("user: name=Alice", EMBEDDING.values(), Map.of());
      final var entry = new MemoryEntry(memory, new Score(0.92));

      when(embeddingPort.embed(anyString()))
          .thenReturn(RichFuture.of(CompletableFuture.completedFuture(EMBEDDING)));
      when(memoryStore.query(any()))
          .thenReturn(RichFuture.of(CompletableFuture.completedFuture(List.of(entry))));

      final var result = invokeQueryMemory("Alice", null);

      assertThat(result.isError()).isNotEqualTo(true);
      assertThat(result.content()).hasSize(1);
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("user: name=Alice")
          .contains("0.92");
    }

    @Test
    @DisplayName("should return no-match message when no memories are found")
    void shouldReturnNoMatchMessageWhenNoMemoriesAreFound() throws Exception {
      when(embeddingPort.embed(anyString()))
          .thenReturn(RichFuture.of(CompletableFuture.completedFuture(EMBEDDING)));
      when(memoryStore.query(any()))
          .thenReturn(RichFuture.of(CompletableFuture.completedFuture(List.of())));

      final var result = invokeQueryMemory("unknown criteria", null);

      assertThat(result.isError()).isNotEqualTo(true);
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("No matching memories found");
    }

    @Test
    @DisplayName("should use provided maxResults when supplied")
    void shouldUseProvidedMaxResultsWhenSupplied() throws Exception {
      when(embeddingPort.embed(anyString()))
          .thenReturn(RichFuture.of(CompletableFuture.completedFuture(EMBEDDING)));
      when(memoryStore.query(any()))
          .thenReturn(RichFuture.of(CompletableFuture.completedFuture(List.of())));

      final var result = invokeQueryMemory("test", 10);

      assertThat(result.isError()).isNotEqualTo(true);
    }

    @Test
    @DisplayName("should return error result when embedding port throws")
    void shouldReturnErrorResultWhenEmbeddingPortThrows() throws Exception {
      when(embeddingPort.embed(anyString()))
          .thenReturn(RichFuture.of(
              CompletableFuture.failedFuture(new RuntimeException("embed error"))));

      final var result = invokeQueryMemory("crash", null);

      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("embed error");
    }

    @Test
    @DisplayName("should return error result when memory store query throws")
    void shouldReturnErrorResultWhenMemoryStoreQueryThrows() throws Exception {
      when(embeddingPort.embed(anyString()))
          .thenReturn(RichFuture.of(CompletableFuture.completedFuture(EMBEDDING)));
      when(memoryStore.query(any()))
          .thenReturn(RichFuture.of(
              CompletableFuture.failedFuture(new RuntimeException("store error"))));

      final var result = invokeQueryMemory("crash", null);

      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("store error");
    }
  }

  @Nested
  @DisplayName("given a process_prompt call")
  class GivenAProcessPromptCall {

    @Test
    @DisplayName("should return the answer text when the pipeline succeeds")
    void shouldReturnAnswerTextWhenPipelineSucceeds() throws Exception {
      when(promptHandler.process(anyString()))
          .thenReturn(new PromptResponse.Answer("42 is the answer"));

      final var result = invokeProcessPrompt("What is the answer?");

      assertThat(result.isError()).isNotEqualTo(true);
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .isEqualTo("42 is the answer");
    }

    @Test
    @DisplayName("should return error result when the pipeline returns Failure")
    void shouldReturnErrorResultWhenPipelineReturnsFailure() throws Exception {
      when(promptHandler.process(anyString()))
          .thenReturn(new PromptResponse.Failure("pipeline failed"));

      final var result = invokeProcessPrompt("bad prompt");

      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("pipeline failed");
    }

    @Test
    @DisplayName("should return error result when the handler throws")
    void shouldReturnErrorResultWhenHandlerThrows() throws Exception {
      when(promptHandler.process(anyString()))
          .thenThrow(new RuntimeException("unexpected error"));

      final var result = invokeProcessPrompt("crash prompt");

      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("unexpected error");
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private McpSchema.CallToolResult invokeQueryMemory(
      final String criteria, final Integer maxResults) {
    final var args = new HashMap<String, Object>();
    args.put("criteria", criteria);
    if (maxResults != null) {
      args.put("maxResults", maxResults);
    }
    return server.handleQueryMemory(null, new McpSchema.CallToolRequest("query_memory", args));
  }

  private McpSchema.CallToolResult invokeProcessPrompt(final String prompt) throws Exception {
    return server.handleProcessPrompt(null,
        new McpSchema.CallToolRequest("process_prompt", Map.of("prompt", prompt)));
  }
}
