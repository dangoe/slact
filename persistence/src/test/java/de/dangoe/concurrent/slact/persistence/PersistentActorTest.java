package de.dangoe.concurrent.slact.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.persistence.testkit.InMemoryEventStore;
import de.dangoe.concurrent.slact.testkit.Constants;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.time.Clock;
import java.util.Optional;
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

  private sealed interface CounterMessage permits CounterMessage.CurrentCount,
      CounterMessage.GetCount, CounterMessage.Increment {

    record Increment() implements CounterMessage {

    }

    record GetCount() implements CounterMessage {

    }

    record CurrentCount(int value) implements CounterMessage {

    }
  }

  private record Incremented() {

  }

  private static class CounterActor extends SimplePersistentActor<CounterMessage, Incremented> {

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

  private final InMemoryEventStore<Incremented> eventStore = new InMemoryEventStore<>(
      Clock.systemUTC());

  @BeforeEach
  void setUp() {

    PersistenceExtensionHolder.getInstance().register(new PersistenceExtension() {

      @Override
      @SuppressWarnings("unchecked")
      public <E, S extends SnapshotPayload> @NotNull Optional<EventStore<E, S>> resolveStore(
          final @NotNull PartitionKey key) {
        return Optional.of((EventStore<E, S>) eventStore);
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

      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);

      final var countAfterFirstRun = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount())
          .ofType(CounterMessage.CurrentCount.class).from(counterV1);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterFirstRun::isDone);
      assertThat(countAfterFirstRun.get()).isEqualTo(new CounterMessage.CurrentCount(3));

      container.stop(counterV1).get(5, TimeUnit.SECONDS);

      final var secondRecoveryLatch = new CountDownLatch(1);
      final var counterV2 = container.spawn("counter-v2", () -> new CounterActor() {
        @Override
        protected void afterRecovery() {
          secondRecoveryLatch.countDown();
        }
      });

      assertThat(secondRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      final var countAfterRecovery = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount())
          .ofType(CounterMessage.CurrentCount.class).from(counterV2);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterRecovery::isDone);
      assertThat(countAfterRecovery.get()).isEqualTo(new CounterMessage.CurrentCount(3));

      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV2);
      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV2);

      final var countAfterMoreIncrements = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount()).ofType(CounterMessage.CurrentCount.class)
          .from(counterV2);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterMoreIncrements::isDone);
      assertThat(countAfterMoreIncrements.get()).isEqualTo(new CounterMessage.CurrentCount(5));
    }
  }
}
