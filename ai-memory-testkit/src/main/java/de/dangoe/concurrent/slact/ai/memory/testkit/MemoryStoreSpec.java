package de.dangoe.concurrent.slact.ai.memory.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import de.dangoe.concurrent.slact.ai.memory.Embedding;
import de.dangoe.concurrent.slact.ai.memory.Memory;
import de.dangoe.concurrent.slact.ai.memory.MemoryQuery;
import de.dangoe.concurrent.slact.ai.memory.MemoryStore;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Abstract specification for the {@link MemoryStore} contract.
 *
 * <p>Subclasses provide the concrete infrastructure via the template methods.
 */
@DisplayName("Memory store")
public abstract class MemoryStoreSpec {

  private MemoryStore store;

  /**
   * Creates a fresh {@link MemoryStore} instance backed by the concrete infrastructure.
   *
   * @return a new {@link MemoryStore} backed by the test infrastructure.
   */
  protected abstract @NotNull MemoryStore createStore();

  /**
   * Resets the store to a clean state before each test.
   *
   * @throws Exception if the cleanup fails.
   */
  protected abstract void cleanStore() throws Exception;

  /**
   * Creates a new spec instance.
   */
  protected MemoryStoreSpec() {
    super();
  }

  @BeforeEach
  final void setUpStore() throws Exception {
    cleanStore();
    store = createStore();
  }

  @Nested
  @DisplayName("Given a memory is saved")
  class GivenAMemoryIsSaved {

    private static final Embedding EMBEDDING = new Embedding(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

    @Test
    @DisplayName("save returns the ID of the stored memory")
    void saveReturnsIdOfStoredMemory() {
      final var memory = Memory.of("test content", EMBEDDING.values(), Map.of());

      final var id = store.save(memory).join();

      assertThat(id).isEqualTo(memory.id());
    }

    @Test
    @DisplayName("querying with the same embedding returns at least one result")
    void queryingWithSameEmbeddingReturnsResult() {
      final var memory = Memory.of("test content", EMBEDDING.values(), Map.of());
      store.save(memory).join();

      final var results = store.query(new MemoryQuery(EMBEDDING, 1)).join();

      assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("querying with the same embedding returns a positive score")
    void queryingWithSameEmbeddingReturnsPositiveScore() {
      final var memory = Memory.of("test content", EMBEDDING.values(), Map.of());
      store.save(memory).join();

      final var results = store.query(new MemoryQuery(EMBEDDING, 1)).join();

      assertThat(results.get(0).score().value()).isGreaterThan(0.0);
    }
  }

  @Nested
  @DisplayName("Given an empty store")
  class GivenAnEmptyStore {

    private static final Embedding EMBEDDING = new Embedding(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

    @Test
    @DisplayName("querying returns an empty list")
    void queryingReturnsEmptyList() {
      final var results = store.query(new MemoryQuery(EMBEDDING, 5)).join();

      assertThat(results).isEmpty();
    }
  }
}
