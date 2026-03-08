package de.dangoe.concurrent.slact.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Given a persistent actor")
@ExtendWith(SlactTestContainerExtension.class)
public class PersistentActorTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private sealed interface CounterMessage permits Increment, GetCount, CurrentCount {

  }

  private record Increment() implements CounterMessage {

  }

  private record GetCount() implements CounterMessage {

  }

  private record CurrentCount(int value) implements CounterMessage {

  }

  private record Incremented() {

  }

  private static class CounterActor extends PersistentActor<CounterMessage, Incremented> {

    @Override
    protected @NotNull PartitionKey partitionKey() {
      return PartitionKey.of("counter-1");
    }

    @Override
    public void onMessage(final @NotNull CounterMessage message) {
      switch (message) {
        case Increment() -> persist(new Incremented());
        case GetCount() -> respondWith(new CurrentCount(events().size()));
        case CurrentCount ignored -> reject(message);
      }
    }
  }

  @SuppressWarnings("rawtypes")
  private static final class InMemoryEventStore implements EventStore {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Object>> store = new ConcurrentHashMap<>();

    @Override
    public @NotNull CompletableFuture<List> loadEvents(final @NotNull PartitionKey partitionKey) {
      return CompletableFuture.completedFuture(
          List.copyOf(store.getOrDefault(partitionKey.value(), new CopyOnWriteArrayList<>())));
    }

    @Override
    public @NotNull CompletableFuture<Void> appendMultiple(final @NotNull PartitionKey partitionKey,
        final @NotNull List events) {
      store.computeIfAbsent(partitionKey.value(), k -> new CopyOnWriteArrayList<>()).addAll(events);
      return CompletableFuture.completedFuture(null);
    }
  }

  private final InMemoryEventStore eventStore = new InMemoryEventStore();

  @BeforeEach
  void setUp() {
    PersistenceExtensionHolder.getInstance().register(new PersistenceExtension() {
      @Override
      @SuppressWarnings("unchecked")
      public <S> @NotNull Optional<EventStore<S>> resolveStore(final @NotNull PartitionKey key) {
        return Optional.of(eventStore);
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

      assertThat(firstRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      container.send((CounterMessage) new Increment()).to(counterV1);
      container.send((CounterMessage) new Increment()).to(counterV1);
      container.send((CounterMessage) new Increment()).to(counterV1);

      final var countAfterFirstRun = container.requestResponseTo((CounterMessage) new GetCount())
          .ofType(CurrentCount.class).from(counterV1);

      await().atMost(TIMEOUT).until(countAfterFirstRun::isDone);
      assertThat(countAfterFirstRun.get()).isEqualTo(new CurrentCount(3));

      container.stop(counterV1).get(5, TimeUnit.SECONDS);

      final var secondRecoveryLatch = new CountDownLatch(1);
      final var counterV2 = container.spawn("counter-v2", () -> new CounterActor() {
        @Override
        protected void afterRecovery() {
          secondRecoveryLatch.countDown();
        }
      });

      assertThat(secondRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      final var countAfterRecovery = container.requestResponseTo((CounterMessage) new GetCount())
          .ofType(CurrentCount.class).from(counterV2);

      await().atMost(TIMEOUT).until(countAfterRecovery::isDone);
      assertThat(countAfterRecovery.get()).isEqualTo(new CurrentCount(3));

      container.send((CounterMessage) new Increment()).to(counterV2);
      container.send((CounterMessage) new Increment()).to(counterV2);

      final var countAfterMoreIncrements = container.requestResponseTo(
          (CounterMessage) new GetCount()).ofType(CurrentCount.class).from(counterV2);

      await().atMost(TIMEOUT).until(countAfterMoreIncrements::isDone);
      assertThat(countAfterMoreIncrements.get()).isEqualTo(new CurrentCount(5));
    }
  }
}
