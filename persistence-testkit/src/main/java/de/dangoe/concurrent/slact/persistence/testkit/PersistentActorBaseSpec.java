package de.dangoe.concurrent.slact.persistence.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.PersistenceExtension;
import de.dangoe.concurrent.slact.persistence.PersistenceExtensionHolder;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase.RecoveryData;
import de.dangoe.concurrent.slact.persistence.testkit.PersistentActorBaseSpec.Incremented;
import de.dangoe.concurrent.slact.testkit.Constants;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
abstract class PersistentActorBaseSpec<R extends RecoveryData<Incremented>, ST extends EventStore> {

  public sealed interface CounterMessage permits CounterMessage.CurrentCount,
      CounterMessage.GetCount, CounterMessage.Increment {

    record Increment() implements PersistentActorBaseSpec.CounterMessage {

    }

    record GetCount() implements PersistentActorBaseSpec.CounterMessage {

    }

    record CurrentCount(int value) implements PersistentActorBaseSpec.CounterMessage {

    }
  }

  public record Incremented() implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
  }

  @BeforeEach
  void setUp() {
    PersistenceExtensionHolder.getInstance()
        .register(createPersistenceExtension(createEventStore()));
  }

  @AfterEach
  void tearDown() {
    PersistenceExtensionHolder.getInstance().clear();
  }

  protected abstract @NotNull PersistenceExtension createPersistenceExtension(@NotNull ST store);

  protected abstract @NotNull PersistentActorBase<CounterMessage, Incremented, R, ST> createSut(
      @NotNull Runnable afterRecoveryHook);

  protected abstract @NotNull ST createEventStore();

  @Nested
  @DisplayName("When events are persisted and the actor is restarted")
  class WhenEventsArePersistedAndTheActorIsRestarted {

    @Test
    @DisplayName("Then the event log is fully recovered on restart")
    void thenEventLogIsFullyRecoveredOnRestart(final @NotNull SlactTestContainer container)
        throws Exception {

      final var firstRecoveryLatch = new CountDownLatch(1);
      final var counterV1 = container.spawn("counter-v1",
          () -> createSut(firstRecoveryLatch::countDown));

      assertThat(firstRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(counterV1);

      final var countAfterFirstRun = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount()).ofType(CounterMessage.CurrentCount.class)
          .from(counterV1);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterFirstRun::isDone);
      assertThat(countAfterFirstRun.get()).isEqualTo(new CounterMessage.CurrentCount(3));

      container.stop(counterV1).get(5, TimeUnit.SECONDS);

      final var secondRecoveryLatch = new CountDownLatch(1);
      final var counterV2 = container.spawn("counter-v2",
          () -> createSut(secondRecoveryLatch::countDown));

      assertThat(secondRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      final var countAfterRecovery = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount()).ofType(CounterMessage.CurrentCount.class)
          .from(counterV2);

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

  @Nested
  @DisplayName("When the actor starts with an empty event store")
  class WhenActorStartsWithEmptyEventStore {

    @Test
    @DisplayName("Then the actor starts with zero events and responds to messages normally")
    void thenActorStartsWithNoEventsAndRespondsNormally(final @NotNull SlactTestContainer container)
        throws Exception {

      final var recoveryLatch = new CountDownLatch(1);
      final var counter = container.spawn("fresh-counter",
          () -> createSut(recoveryLatch::countDown));

      assertThat(recoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      final var count = container.requestResponseTo((CounterMessage) new CounterMessage.GetCount())
          .ofType(CounterMessage.CurrentCount.class).from(counter);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(count::isDone);
      assertThat(count.get()).isEqualTo(new CounterMessage.CurrentCount(0));
    }
  }
}
