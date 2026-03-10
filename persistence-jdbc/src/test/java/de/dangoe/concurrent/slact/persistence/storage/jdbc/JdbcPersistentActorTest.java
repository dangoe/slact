package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zaxxer.hikari.HikariConfig;
import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.PersistenceExtension;
import de.dangoe.concurrent.slact.persistence.PersistenceExtensionHolder;
import de.dangoe.concurrent.slact.persistence.PersistentActor;
import de.dangoe.concurrent.slact.testkit.Constants;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.io.Serial;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ExtendWith(SlactTestContainerExtension.class)
@Testcontainers
@DisplayName("Given a persistent actor backed by a JDBC event store")
public class JdbcPersistentActorTest {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  private static HikariConnectionPool connectionPool;
  private static ExecutorService executorService;

  private sealed interface CounterMessage permits CounterMessage.CurrentCount,
      CounterMessage.GetCount, CounterMessage.Increment {

    record Increment() implements CounterMessage {

    }

    record GetCount() implements CounterMessage {

    }

    record CurrentCount(int value) implements CounterMessage {

    }
  }

  private record Incremented() implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
  }

  private static class CounterActor extends PersistentActor<CounterMessage, Incremented> {

    @Override
    protected @NotNull PartitionKey partitionKey() {
      return PartitionKey.of("counter-1");
    }

    @Override
    public void onMessage(final @NotNull CounterMessage message) {
      switch (message) {
        case CounterMessage.Increment() -> persist(new Incremented());
        case CounterMessage.GetCount() ->
            respondWith(new CounterMessage.CurrentCount(events().size()));
        case CounterMessage.CurrentCount ignored -> reject(message);
      }
    }
  }

  @BeforeAll
  static void setUpDatabase() throws Exception {
    final var config = new HikariConfig();
    config.setJdbcUrl(postgres.getJdbcUrl());
    config.setUsername(postgres.getUsername());
    config.setPassword(postgres.getPassword());
    connectionPool = new HikariConnectionPool(config);

    try (final var connection = connectionPool.acquire()) {
      final var liquibase = new Liquibase(
          "db/changelog/db.changelog-master.yaml",
          new ClassLoaderResourceAccessor(),
          new JdbcConnection(connection));
      liquibase.update();
    }

    executorService = Executors.newFixedThreadPool(4);
  }

  @AfterAll
  static void tearDownDatabase() {
    executorService.shutdown();
    connectionPool.close();
  }

  @BeforeEach
  void setUp() throws InterruptedException, SQLException {
    try (final var connection = connectionPool.acquire();
        final var statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE events RESTART IDENTITY");
    }

    final var eventStore = new JdbcEventStore<Incremented>(connectionPool, executorService, new PostgreSqlDialect());

    PersistenceExtensionHolder.getInstance().register(new PersistenceExtension() {

      @Override
      @SuppressWarnings("unchecked")
      public <S> @NotNull Optional<EventStore<S>> resolveStore(final @NotNull PartitionKey key) {
        return Optional.of((EventStore<S>) eventStore);
      }
    });
  }

  @AfterEach
  void tearDown() {
    PersistenceExtensionHolder.getInstance().clear();
  }

  @Nested
  @DisplayName("When events are persisted and the actor is restarted")
  class WhenEventsArePersistedAndTheActorIsRestarted {

    @Test
    @DisplayName("Then the event log is fully recovered on restart")
    void thenEventLogIsFullyRecoveredOnRestart(final @NotNull SlactTestContainer container)
        throws Exception {

      final var firstRecoveryLatch = new CountDownLatch(1);
      final var counterV1 = container.spawn("counter-v1", () -> new CounterActor() {
        @Override
        protected void afterRecovery() {
          firstRecoveryLatch.countDown();
        }
      });

      assertThat(firstRecoveryLatch.await(10, TimeUnit.SECONDS)).isTrue();

      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);

      final var countAfterFirstRun = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount())
          .ofType(CounterMessage.CurrentCount.class).from(counterV1);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterFirstRun::isDone);
      assertThat(countAfterFirstRun.get()).isEqualTo(new CounterMessage.CurrentCount(3));

      container.stop(counterV1).get(10, TimeUnit.SECONDS);

      final var secondRecoveryLatch = new CountDownLatch(1);
      final var counterV2 = container.spawn("counter-v2", () -> new CounterActor() {
        @Override
        protected void afterRecovery() {
          secondRecoveryLatch.countDown();
        }
      });

      assertThat(secondRecoveryLatch.await(10, TimeUnit.SECONDS)).isTrue();

      final var countAfterRecovery = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount())
          .ofType(CounterMessage.CurrentCount.class).from(counterV2);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterRecovery::isDone);
      assertThat(countAfterRecovery.get()).isEqualTo(new CounterMessage.CurrentCount(3));

      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV2);
      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV2);

      final var countAfterMoreIncrements = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount())
          .ofType(CounterMessage.CurrentCount.class).from(counterV2);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterMoreIncrements::isDone);
      assertThat(countAfterMoreIncrements.get()).isEqualTo(new CounterMessage.CurrentCount(5));
    }
  }
}
