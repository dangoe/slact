package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.PersistentActorBase.RecoveryData;
import de.dangoe.concurrent.slact.persistence.PersistentActorBaseSpec.Incremented;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.io.Serial;
import java.time.Clock;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Given a persistent actor")
@ExtendWith(SlactTestContainerExtension.class)
public class PersistentActorTest extends
    PersistentActorBaseSpec<RecoveryData<Incremented>, EventStore> {

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
  @NotNull EventStore createEventStore() {
    return new InMemoryEventStore(Clock.systemUTC());
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

