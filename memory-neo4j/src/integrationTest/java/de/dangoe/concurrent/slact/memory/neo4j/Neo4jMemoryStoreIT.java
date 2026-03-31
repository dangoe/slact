package de.dangoe.concurrent.slact.memory.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import de.dangoe.concurrent.slact.memory.Embedding;
import de.dangoe.concurrent.slact.memory.MemoryQuery;
import de.dangoe.concurrent.slact.memory.MemoryStore;
import de.dangoe.concurrent.slact.memory.testkit.MemoryStoreSpec;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DisplayName("Neo4jMemoryStore")
class Neo4jMemoryStoreIT extends MemoryStoreSpec {

  private static final int EMBEDDING_DIMENSION = 4;

  @Container
  private static final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5");

  @Override
  protected @NotNull MemoryStore createStore() {
    final var driver = GraphDatabase.driver(
        neo4j.getBoltUrl(),
        AuthTokens.basic("neo4j", neo4j.getAdminPassword()));
    final var store = new Neo4jMemoryStore(driver, "neo4j", EMBEDDING_DIMENSION);
    store.initialize();
    return store;
  }

  @Override
  protected void cleanStore() {
    final var driver = GraphDatabase.driver(
        neo4j.getBoltUrl(),
        AuthTokens.basic("neo4j", neo4j.getAdminPassword()));
    try (final var session = driver.session()) {
      session.run("MATCH (n) DETACH DELETE n");
    }
  }

  @Nested
  @DisplayName("Given an invalid embedding dimension")
  class GivenAnInvalidEmbeddingDimension {

    @Test
    @DisplayName("querying with wrong dimension fails")
    void queryingWithWrongDimensionFails() {
      final var wrongEmbedding = new Embedding(new float[]{0.1f, 0.2f});
      final var query = new MemoryQuery(wrongEmbedding, 1);

      final var thrown = org.assertj.core.api.Assertions.catchThrowableOfType(
          () -> createStore().query(query).join(),
          Exception.class);

      assertThat(thrown).isNotNull();
    }
  }
}
