package de.dangoe.concurrent.slact.memory.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import de.dangoe.concurrent.slact.memory.Memory;
import de.dangoe.concurrent.slact.memory.MemoryQuery;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DisplayName("Neo4jMemoryStore")
class Neo4jMemoryStoreIT {

  private static final int EMBEDDING_DIMENSION = 4;

  @Container
  static final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5")
      .withoutAuthentication();

  private static Neo4jMemoryStore store;

  @BeforeAll
  static void setUp() {
    final var driver = GraphDatabase.driver(neo4j.getBoltUrl());
    store = new Neo4jMemoryStore(driver, "neo4j", EMBEDDING_DIMENSION);
    store.initialize();
  }

  @Nested
  @DisplayName("save()")
  class Save {

    @Test
    @DisplayName("when a memory is saved, then returns its id")
    void whenSaved_thenReturnsId() {
      final var memory = Memory.of("integration test content",
          new float[]{0.1f, 0.2f, 0.3f, 0.4f}, Map.of());

      final var id = store.save(memory).join();

      assertThat(id).isEqualTo(memory.id());
    }
  }

  @Nested
  @DisplayName("query()")
  class Query {

    @Test
    @DisplayName("when queried with the same embedding, then returns an entry with a positive score")
    void whenQueried_thenReturnsMatchingEntry() {
      final var embedding = new float[]{0.5f, 0.5f, 0.5f, 0.5f};
      final var memory = Memory.of("query integration test", embedding, Map.of());
      store.save(memory).join();

      final var query = new MemoryQuery(embedding, 1);
      final var results = store.query(query).join();

      assertThat(results).isNotEmpty();
      assertThat(results.get(0).score()).isGreaterThan(0.0);
    }
  }
}
