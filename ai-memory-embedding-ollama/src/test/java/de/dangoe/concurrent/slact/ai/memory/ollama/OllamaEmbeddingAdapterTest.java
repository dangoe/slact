package de.dangoe.concurrent.slact.ai.memory.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OllamaEmbeddingAdapter")
class OllamaEmbeddingAdapterTest {

  @Nested
  @DisplayName("given a new instance")
  class GivenANewInstance {

    @Test
    @DisplayName("should return a non-null future for valid text input")
    void shouldReturnNonNullFutureForValidTextInput() {
      final var adapter = new OllamaEmbeddingAdapter("http://localhost:11434", "nomic-embed-text");

      // The actual network call is not made here; we only verify the structural contract.
      // Integration tests against a live Ollama instance are expected separately.
      assertThat(adapter.embed("hello")).isNotNull();
    }
  }

  @Nested
  @DisplayName("given constructor arguments")
  class GivenConstructorArguments {

    @Test
    @SuppressWarnings("DataFlowIssue")
    @DisplayName("should throw NullPointerException when baseUrl is null")
    void shouldThrowWhenBaseUrlIsNull() {
      assertThatThrownBy(() -> new OllamaEmbeddingAdapter(null, "model")).isInstanceOf(
          NullPointerException.class);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    @DisplayName("should throw NullPointerException when model is null")
    void shouldThrowWhenModelIsNull() {
      assertThatThrownBy(
          () -> new OllamaEmbeddingAdapter("http://localhost:11434", null)).isInstanceOf(
          NullPointerException.class);
    }
  }
}
