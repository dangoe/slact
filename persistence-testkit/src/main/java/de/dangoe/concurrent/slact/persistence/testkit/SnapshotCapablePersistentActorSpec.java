package de.dangoe.concurrent.slact.persistence.testkit;

import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.PersistenceExtension;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase;
import de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore;
import de.dangoe.concurrent.slact.persistence.SnapshotCapablePersistentActor;
import de.dangoe.concurrent.slact.persistence.SnapshotCapablePersistentActor.SnapshotCapableRecoveryData;
import de.dangoe.concurrent.slact.persistence.SnapshottingStrategy;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public abstract class SnapshotCapablePersistentActorSpec extends
    PersistentActorBaseSpec<SnapshotCapableRecoveryData<PersistentActorBaseSpec.Incremented, Void>, SnapshotCapableEventStore> {

  protected static class CounterActor extends
      SnapshotCapablePersistentActor<CounterMessage, Incremented, Void> {

    @Override
    protected @NotNull PartitionKey<Incremented> partitionKey() {
      return new CounterPartitionKey("counter-1");
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
      public <E> @NotNull Optional<EventStore> resolveStore(final @NotNull PartitionKey<E> key) {
        return Optional.of(store);
      }

      @Override
      public @NotNull <E> Optional<SnapshotCapableEventStore> resolveSnapshotCapableStore(
          final @NotNull PartitionKey<E> key) {
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
}
