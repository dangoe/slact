package de.dangoe.concurrent.slact.persistence.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.PersistenceExtension;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase;
import de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore;
import de.dangoe.concurrent.slact.persistence.SnapshotCapablePersistentActor;
import de.dangoe.concurrent.slact.persistence.SnapshotCapablePersistentActor.SnapshotCapableRecoveryData;
import de.dangoe.concurrent.slact.persistence.SnapshottingStrategy;
import de.dangoe.concurrent.slact.testkit.Constants;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Abstract specification for the
 * {@link de.dangoe.concurrent.slact.persistence.SnapshotCapablePersistentActor} contract, including
 * snapshot creation and recovery scenarios.
 */
public abstract class SnapshotCapablePersistentActorSpec extends
    PersistentActorBaseSpec<SnapshotCapableRecoveryData<PersistentActorBaseSpec.Incremented, Void>, SnapshotCapableEventStore> {

  protected static class CounterActor extends
      SnapshotCapablePersistentActor<CounterMessage, Incremented, Void> {

    @Override
    protected @NotNull PartitionKey partitionKey() {
      return new PartitionKey("counter", "counter-1");
    }

    @Override
    protected @NotNull Class<Void> snapshotType() {
      return Void.class;
    }

    @Override
    protected @NotNull SnapshottingStrategy<Incremented, Void> snapshottingStrategy() {
      return (events, latestSnapshot) -> Optional.empty();
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

  @Override
  protected final @NotNull PersistenceExtension createPersistenceExtension(
      final @NotNull SnapshotCapableEventStore store) {

    return new PersistenceExtension() {

      @Override
      public @NotNull Optional<EventStore> resolveStore(final @NotNull PartitionKey key) {
        return Optional.of(store);
      }

      @Override
      public @NotNull Optional<SnapshotCapableEventStore> resolveSnapshotCapableStore(
          final @NotNull PartitionKey partitionKey) {
        return Optional.of(store);
      }
    };
  }

  @Override
  protected final @NotNull PersistentActorBase<CounterMessage, Incremented, SnapshotCapableRecoveryData<Incremented, Void>, SnapshotCapableEventStore> createSut(
      final @NotNull Runnable afterRecoveryHook) {

    return new CounterActor() {

      @Override
      protected void afterRecovery() {
        super.afterRecovery();
        afterRecoveryHook.run();
      }
    };
  }

  @Override
  protected abstract @NotNull SnapshotCapableEventStore createEventStore();

  @Nested
  @DisplayName("When an actual snapshotting strategy is used")
  class WhenAnActualSnapshottingStrategyIsUsed {

    protected static class SnapshotCounterActor extends
        SnapshotCapablePersistentActor<CounterMessage, Incremented, Integer> {

      @Override
      protected @NotNull PartitionKey partitionKey() {
        return new PartitionKey("counter", "snapshot-counter-1");
      }

      @Override
      protected @NotNull Class<Integer> snapshotType() {
        return Integer.class;
      }

      @Override
      protected @NotNull SnapshottingStrategy<Incremented, Integer> snapshottingStrategy() {
        return (events, latestSnapshot) -> {
          if (events.size() >= 3) {
            final int baseCount = latestSnapshot != null ? latestSnapshot.snapshot() : 0;
            return Optional.of(
                new SnapshottingStrategy.CreatedSnapshot<>(events.getLast().ordering(),
                    baseCount + events.size()));
          }
          return Optional.empty();
        };
      }

      @Override
      public void onMessage(final @NotNull CounterMessage message) {
        switch (message) {
          case CounterMessage.Increment() -> persist(new Incremented());
          case CounterMessage.GetCount() -> respondWith(
              new CounterMessage.CurrentCount(latestSnapshot().orElse(0) + events().size()));
          case CounterMessage.CurrentCount ignored -> reject(message);
        }
      }
    }

    @Test
    @DisplayName("A snapshot is saved and the actor recovers correctly from it")
    void snapshotIsSavedAndActorRecoversCorrectlyFromIt(final @NotNull SlactTestContainer container)
        throws Exception {

      final var firstRecoveryLatch = new CountDownLatch(1);
      final var actorV1 = container.spawn("snapshot-counter-v1", () -> new SnapshotCounterActor() {
        @Override
        protected void afterRecovery() {
          super.afterRecovery();
          firstRecoveryLatch.countDown();
        }
      });
      assertThat(firstRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      container.send((CounterMessage) new CounterMessage.Increment()).to(actorV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(actorV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(actorV1);

      container.stop(actorV1).get(5, TimeUnit.SECONDS);

      final var secondRecoveryLatch = new CountDownLatch(1);
      final var actorV2 = container.spawn("snapshot-counter-v2", () -> new SnapshotCounterActor() {
        @Override
        protected void afterRecovery() {
          super.afterRecovery();
          secondRecoveryLatch.countDown();
        }
      });
      assertThat(secondRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      final var countAfterRecovery = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount()).ofType(CounterMessage.CurrentCount.class)
          .from(actorV2);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterRecovery::isDone);
      assertThat(countAfterRecovery.get()).isEqualTo(new CounterMessage.CurrentCount(3));
    }

    @Test
    @DisplayName("After a snapshot, subsequent events are replayed correctly on restart")
    void afterSnapshotSubsequentEventsAreReplayedCorrectlyOnRestart(
        final @NotNull SlactTestContainer container) throws Exception {

      final var firstRecoveryLatch = new CountDownLatch(1);
      final var actorV1 = container.spawn("snapshot-counter-v1b", () -> new SnapshotCounterActor() {
        @Override
        protected void afterRecovery() {
          super.afterRecovery();
          firstRecoveryLatch.countDown();
        }
      });
      assertThat(firstRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      container.send((CounterMessage) new CounterMessage.Increment()).to(actorV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(actorV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(actorV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(actorV1);
      container.send((CounterMessage) new CounterMessage.Increment()).to(actorV1);

      container.stop(actorV1).get(5, TimeUnit.SECONDS);

      final var secondRecoveryLatch = new CountDownLatch(1);
      final var actorV2 = container.spawn("snapshot-counter-v2b", () -> new SnapshotCounterActor() {
        @Override
        protected void afterRecovery() {
          super.afterRecovery();
          secondRecoveryLatch.countDown();
        }
      });
      assertThat(secondRecoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();

      final var countAfterRecovery = container.requestResponseTo(
              (CounterMessage) new CounterMessage.GetCount()).ofType(CounterMessage.CurrentCount.class)
          .from(actorV2);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(countAfterRecovery::isDone);
      assertThat(countAfterRecovery.get()).isEqualTo(new CounterMessage.CurrentCount(5));
    }
  }
}
