package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.PersistentActorBaseSpec.Incremented;
import de.dangoe.concurrent.slact.persistence.SnapshotCapablePersistentActor.SnapshotCapableRecoveryData;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.io.Serial;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Given a snapshot capable persistent actor")
@ExtendWith(SlactTestContainerExtension.class)
public class SnapshotCapablePersistentActorTest extends
    PersistentActorBaseSpec<SnapshotCapableRecoveryData<Incremented, Void>, SnapshotCapableEventStore> {

  private record CounterPartitionKey(@NotNull String value) implements PartitionKey<Incremented> {

    @Serial
    private static final long serialVersionUID = 1L;

    CounterPartitionKey {
      Objects.requireNonNull(value, "Value must not be null!");
      if (value.isBlank()) {
        throw new IllegalArgumentException("Value must not be blank!");
      }
    }

    @Override
    public Class<Incremented> eventType() {
      return Incremented.class;
    }
  }

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
  @NotNull SnapshotCapableEventStore createEventStore() {
    return new SnapshotCapableInMemoryEventStore(Clock.systemUTC());
  }

  @Override
  @NotNull CounterActor createSut(final @NotNull Runnable afterRecoveryHook) {

    return new CounterActor() {

      @Override
      protected void afterRecovery() {
        super.afterRecovery();
        afterRecoveryHook.run();
      }
    };
  }
}

