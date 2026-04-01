package de.dangoe.concurrent.slact.ai.memory.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dangoe.concurrent.slact.ai.memory.Embedding;
import de.dangoe.concurrent.slact.ai.memory.EmbeddingPort;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link EmbeddingPort} adapter backed by a locally running
 * <a href="https://ollama.com">Ollama</a> instance.
 *
 * <p>Calls {@code POST /api/embed} on the configured Ollama server.
 * The response's first embedding vector is returned as an {@link Embedding}.
 *
 * <p>Example setup (docker-compose):
 * <pre>
 *   ollama:
 *     image: ollama/ollama
 *     ports:
 *       - "11434:11434"
 * </pre>
 * After starting, pull the desired model once:
 * <pre>
 *   docker exec -it &lt;container&gt; ollama pull nomic-embed-text
 * </pre>
 */
public final class OllamaEmbeddingAdapter implements EmbeddingPort {

  private static final String EMBED_PATH = "/api/embed";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger logger = LoggerFactory.getLogger(OllamaEmbeddingAdapter.class);

  private final @NotNull HttpClient httpClient;
  private final @NotNull String baseUrl;
  private final @NotNull String model;

  /**
   * Creates an {@link OllamaEmbeddingAdapter} that communicates with the given Ollama server.
   *
   * @param baseUrl the base URL of the Ollama server (e.g. {@code http://localhost:11434});
   *                must not be {@code null}.
   * @param model   the Ollama model name to use for embedding (e.g. {@code nomic-embed-text});
   *                must not be {@code null}.
   */
  public OllamaEmbeddingAdapter(
      final @NotNull String baseUrl,
      final @NotNull String model) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "Base URL must not be null");
    this.model = Objects.requireNonNull(model, "Model must not be null");
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public @NotNull RichFuture<Embedding> embed(final @NotNull String text) {
    try {
      final ObjectNode requestBody = OBJECT_MAPPER.createObjectNode()
          .put("model", model)
          .put("input", text);
      final var requestJson = OBJECT_MAPPER.writeValueAsString(requestBody);

      final var request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + EMBED_PATH))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestJson))
          .build();

      final var future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(response -> {
            if (response.statusCode() != 200) {
              throw new RuntimeException(
                  "Ollama embed request failed with HTTP " + response.statusCode()
                      + ": " + response.body());
            }
            try {
              final var responseNode = OBJECT_MAPPER.readTree(response.body());
              final var embeddingsNode = responseNode.get("embeddings");
              if (embeddingsNode == null || embeddingsNode.isEmpty()) {
                throw new RuntimeException(
                    "Ollama response contains no embeddings: " + response.body());
              }
              final var vectorNode = embeddingsNode.get(0);
              final float[] values = new float[vectorNode.size()];
              for (int i = 0; i < vectorNode.size(); i++) {
                values[i] = (float) vectorNode.get(i).asDouble();
              }
              logger.debug("Embedded text ({} chars) → {} dimensions", text.length(), values.length);
              return new Embedding(values);
            } catch (final Exception e) {
              throw new RuntimeException(
                  "Failed to parse Ollama embed response: " + response.body(), e);
            }
          });

      return RichFuture.of(future);
    } catch (final Exception e) {
      return RichFuture.of(CompletableFuture.failedFuture(e));
    }
  }
}
