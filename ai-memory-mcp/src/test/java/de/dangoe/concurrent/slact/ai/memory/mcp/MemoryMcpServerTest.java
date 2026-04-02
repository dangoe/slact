package de.dangoe.concurrent.slact.ai.memory.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dangoe.concurrent.slact.ai.memory.Embedding;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryMcpServer")
class MemoryMcpServerTest {

  private static final Embedding EMBEDDING = new Embedding(new float[]{0.1f, 0.2f, 0.3f});

  private MemoryMcpServer.MemoryQueryHandler memoryQueryHandler;
  private MemoryStore memoryStore;
  private MemoryMcpServer.PromptHandler promptHandler;
  private MemoryMcpServer server;

  @BeforeEach
  void setUp() {
    memoryQueryHandler = mock(MemoryMcpServer.MemoryQueryHandler.class);
    memoryStore = mock(MemoryStore.class);
    promptHandler = mock(MemoryMcpServer.PromptHandler.class);
    server = new MemoryMcpServer(memoryQueryHandler, memoryStore, promptHandler);
  }

  @Nested
  @DisplayName("given a query_memory call")
  class GivenAQueryMemoryCall {

    @Test
    @DisplayName("should return formatted memory entries when matches are found")
    void shouldReturnFormattedMemoryEntriesWhenMatchesAreFound() throws Exception {
      final var memory = Memory.of("user: name=Alice", EMBEDDING.values(), Map.of());
      final var entry = new MemoryEntry(memory, new Score(0.92));

      when(memoryQueryHandler.query(anyString(), anyInt()))
          .thenReturn(List.of(entry));

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
      when(memoryQueryHandler.query(anyString(), anyInt()))
          .thenReturn(List.of());

      final var result = invokeQueryMemory("unknown topic", null);

      assertThat(result.isError()).isNotEqualTo(true);
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("No matching memories found");
    }

    @Test
    @DisplayName("should use provided maxResults when supplied")
    void shouldUseProvidedMaxResultsWhenSupplied() throws Exception {
      when(memoryQueryHandler.query(anyString(), anyInt()))
          .thenReturn(List.of());

      final var result = invokeQueryMemory("test", 10);

      assertThat(result.isError()).isNotEqualTo(true);
    }

    @Test
    @DisplayName("should return error result when the query handler throws")
    void shouldReturnErrorResultWhenQueryHandlerThrows() throws Exception {
      when(memoryQueryHandler.query(anyString(), anyInt()))
          .thenThrow(new RuntimeException("query error"));

      final var result = invokeQueryMemory("crash", null);

      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("query error");
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
          .thenReturn(new PromptResponse.Failure("pipeline failed",
              new RuntimeException("pipeline failed")));

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

  @Nested
  @DisplayName("given a delete_memory call")
  class GivenADeleteMemoryCall {

    @Test
    @DisplayName("should return confirmation message when deletion succeeds")
    void shouldReturnConfirmationMessageWhenDeletionSucceeds() throws Exception {
      final var id = UUID.randomUUID();
      when(memoryStore.delete(id))
          .thenReturn(RichFuture.of(CompletableFuture.completedFuture(null)));

      final var result = invokeDeleteMemory(id.toString());

      assertThat(result.isError()).isNotEqualTo(true);
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains(id.toString());
    }

    @Test
    @DisplayName("should return error result when memory store delete throws")
    void shouldReturnErrorResultWhenMemoryStoreDeleteThrows() throws Exception {
      final var id = UUID.randomUUID();
      when(memoryStore.delete(id))
          .thenReturn(RichFuture.of(
              CompletableFuture.failedFuture(new RuntimeException("delete error"))));

      final var result = invokeDeleteMemory(id.toString());

      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("delete error");
    }

    @Test
    @DisplayName("should return error result when UUID format is invalid")
    void shouldReturnErrorResultWhenUuidFormatIsInvalid() {
      final var result = invokeDeleteMemory("not-a-uuid");

      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
          .contains("invalid UUID format");
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private McpSchema.CallToolResult invokeQueryMemory(
      final String topic, final Integer maxResults) {
    final var args = new HashMap<String, Object>();
    args.put("topic", topic);
    if (maxResults != null) {
      args.put("maxResults", maxResults);
    }
    return server.handleQueryMemory(null, new McpSchema.CallToolRequest("query_memory", args));
  }

  private McpSchema.CallToolResult invokeDeleteMemory(final String id) {
    return server.handleDeleteMemory(null,
        new McpSchema.CallToolRequest("delete_memory", Map.of("id", id)));
  }

  private McpSchema.CallToolResult invokeProcessPrompt(final String prompt) throws Exception {
    return server.handleProcessPrompt(null,
        new McpSchema.CallToolRequest("process_prompt", Map.of("prompt", prompt)));
  }
}
