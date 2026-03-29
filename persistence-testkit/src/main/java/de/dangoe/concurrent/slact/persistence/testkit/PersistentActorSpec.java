package de.dangoe.concurrent.slact.persistence.testkit;

import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.PersistenceExtension;
import de.dangoe.concurrent.slact.persistence.PersistentActor;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase.RecoveryData;
import de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public abstract class PersistentActorSpec
    extends PersistentActorBaseSpec<RecoveryData<PersistentActorBaseSpec.Incremented>, EventStore> {

  protected static class CounterActor extends PersistentActor<CounterMessage, Incremented> {

    @Override
    protected @NotNull PartitionKey<Incremented> partitionKey() {
      return new CounterPartitionKey("counter-1");
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
      final @NotNull EventStore store) {
    return new PersistenceExtension() {

      @Override
      public <E> @NotNull Optional<EventStore> resolveStore(final @NotNull PartitionKey<E> key) {
        return Optional.of(store);
      }

      @Override
      public @NotNull <E> Optional<SnapshotCapableEventStore> resolveSnapshotCapableStore(
          final @NotNull PartitionKey<E> key) {
        throw new UnsupportedOperationException("Snapshot capable store is not supported");
      }
    };
  }

  @Override
  protected final @NotNull PersistentActorBase<CounterMessage, Incremented, RecoveryData<Incremented>, EventStore> createSut(
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
  protected abstract @NotNull EventStore createEventStore();
}
