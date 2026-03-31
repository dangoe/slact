package de.dangoe.concurrent.slact.memory.neo4j;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.memory.Embedding;
import de.dangoe.concurrent.slact.memory.Memory;
import de.dangoe.concurrent.slact.memory.MemoryEntry;
import de.dangoe.concurrent.slact.memory.MemoryQuery;
import de.dangoe.concurrent.slact.memory.MemoryStore;
import de.dangoe.concurrent.slact.memory.Score;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.async.AsyncSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MemoryStore} implementation backed by a Neo4j graph database.
 *
 * <p>Memories are stored as {@code (:Memory)} nodes with an embedding property. A vector index
 * is created on startup to support efficient similarity queries.
 *
 * <p>Call {@link #initialize()} once before using the store to create the vector index.
 */
public final class Neo4jMemoryStore implements MemoryStore {

  private static final String INDEX_NAME = "memory_embedding_idx";
  private static final String NODE_LABEL = "Memory";
  private static final Logger logger = LoggerFactory.getLogger(Neo4jMemoryStore.class);

  private final @NotNull Driver driver;
  private final @NotNull String database;
  private final int embeddingDimension;

  /**
   * Creates a new Neo4j-backed memory store.
   *
   * @param driver             the Neo4j driver instance.
   * @param database           the name of the Neo4j database to use.
   * @param embeddingDimension the dimensionality of the embedding vectors.
   */
  public Neo4jMemoryStore(
      final @NotNull Driver driver,
      final @NotNull String database,
      final int embeddingDimension) {
    this.driver = Objects.requireNonNull(driver, "Driver must not be null");
    this.database = Objects.requireNonNull(database, "Database must not be null");
    this.embeddingDimension = embeddingDimension;
  }

  /**
   * Creates the vector index in the Neo4j database if it does not already exist.
   *
   * <p>Must be called once before using {@link #save} or {@link #query}.
   */
  public void initialize() {
    logger.info("Initializing Neo4jMemoryStore with embedding dimension {}", embeddingDimension);
    try (final var session = driver.session(SessionConfig.forDatabase(database))) {
      session.run("""
              CREATE VECTOR INDEX %s IF NOT EXISTS
              FOR (m:%s) ON (m.embedding)
              OPTIONS {indexConfig: {
                  `vector.dimensions`: %d,
                  `vector.similarity_function`: 'cosine'
              }}
              """.formatted(INDEX_NAME, NODE_LABEL, embeddingDimension));
    }
    logger.info("Neo4jMemoryStore initialized.");
  }

  @Override
  public @NotNull RichFuture<UUID> save(final @NotNull Memory memory) {
    final var session = driver.session(AsyncSession.class, SessionConfig.forDatabase(database));
    final var future = session
        .runAsync(
            "CREATE (m:%s {id: $id, content: $content, embedding: $embedding, createdAt: $createdAt}) RETURN m.id AS id"
                .formatted(NODE_LABEL),
            Map.of(
                "id", memory.id().toString(),
                "content", memory.content(),
                "embedding", toDoubleList(memory.embedding().values()),
                "createdAt", memory.createdAt().toString()))
        .thenCompose(cursor -> cursor.singleAsync())
        .thenApply(record -> UUID.fromString(record.get("id").asString()))
        .whenComplete((result, error) -> session.closeAsync())
        .toCompletableFuture();
    return RichFuture.of(future);
  }

  @Override
  public @NotNull RichFuture<List<MemoryEntry>> query(final @NotNull MemoryQuery query) {
    final var session = driver.session(AsyncSession.class, SessionConfig.forDatabase(database));
    final List<MemoryEntry> entries = new ArrayList<>();
    final var future = session
        .runAsync(
            """
            CALL db.index.vector.queryNodes($index, $maxResults, $embedding)
            YIELD node AS m, score
            RETURN m.id AS id, m.content AS content, m.embedding AS embedding,
                   m.createdAt AS createdAt, score
            """,
            Map.of(
                "index", INDEX_NAME,
                "maxResults", query.maxResults(),
                "embedding", toDoubleList(query.embedding().values())))
        .thenCompose(cursor -> cursor.forEachAsync(record -> {
          final var id = UUID.fromString(record.get("id").asString());
          final var content = record.get("content").asString();
          final var embeddingList = record.get("embedding").asList(v -> (float) v.asDouble());
          final var createdAt = Instant.parse(record.get("createdAt").asString());
          final var score = new Score(record.get("score").asDouble());
          final float[] embeddingArray = new float[embeddingList.size()];
          for (int i = 0; i < embeddingList.size(); i++) {
            embeddingArray[i] = embeddingList.get(i);
          }
          entries.add(new MemoryEntry(
              new Memory(id, content, new Embedding(embeddingArray), Map.of(), createdAt), score));
        }))
        .thenApply(ignored -> entries)
        .whenComplete((result, error) -> session.closeAsync())
        .toCompletableFuture();
    return RichFuture.of(future);
  }

  private @NotNull List<Double> toDoubleList(final float @NotNull [] array) {
    final var result = new ArrayList<Double>(array.length);
    for (final float value : array) {
      result.add((double) value);
    }
    return result;
  }
}
