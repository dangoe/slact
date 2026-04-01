package de.dangoe.concurrent.slact.ai.memory.neo4j;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.ai.memory.Embedding;
import de.dangoe.concurrent.slact.ai.memory.Memory;
import de.dangoe.concurrent.slact.ai.memory.MemoryEntry;
import de.dangoe.concurrent.slact.ai.memory.MemoryQuery;
import de.dangoe.concurrent.slact.ai.memory.MemoryStore;
import de.dangoe.concurrent.slact.ai.memory.Score;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.async.AsyncSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Neo4jMemoryStore implements MemoryStore {

  private static final String INDEX_NAME = "memory_embedding_idx";
  private static final String NODE_LABEL = "Memory";
  private static final Logger logger = LoggerFactory.getLogger(Neo4jMemoryStore.class);

  private final @NotNull Driver driver;
  private final @NotNull String database;
  private final int embeddingDimension;
  private final double similarityThreshold;

  public Neo4jMemoryStore(
      final @NotNull Driver driver,
      final @NotNull String database,
      final int embeddingDimension) {
    this(driver, database, embeddingDimension, 0.0);
  }

  public Neo4jMemoryStore(
      final @NotNull Driver driver,
      final @NotNull String database,
      final int embeddingDimension,
      final double similarityThreshold) {
    this.driver = Objects.requireNonNull(driver, "Driver must not be null");
    this.database = Objects.requireNonNull(database, "Database must not be null");
    this.embeddingDimension = embeddingDimension;
    if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
      throw new IllegalArgumentException(
          "Similarity threshold must be between 0.0 and 1.0, but was: " + similarityThreshold);
    }
    this.similarityThreshold = similarityThreshold;
  }

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
    if (similarityThreshold > 0.0) {
      return query(new MemoryQuery(memory.embedding(), 1))
          .thenCompose(entries -> {
            if (!entries.isEmpty()
                && entries.get(0).score().value() >= similarityThreshold) {
              logger.debug("Deduplication: reusing existing memory {} (score {})",
                  entries.get(0).memory().id(), entries.get(0).score().value());
              return RichFuture.of(
                  CompletableFuture.completedFuture(entries.get(0).memory().id()));
            }
            return doSave(memory);
          });
    }
    return doSave(memory);
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

  private @NotNull RichFuture<UUID> doSave(final @NotNull Memory memory) {
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

  private @NotNull List<Double> toDoubleList(final float @NotNull [] array) {
    final var result = new ArrayList<Double>(array.length);
    for (final float value : array) {
      result.add((double) value);
    }
    return result;
  }
}
