package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.Actor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public abstract class PersistentActor<M, E> extends Actor<M> {

  private record RecoveryResultMessage<E>(@NotNull List<E> events) {

  }

  private record RecoveryFailureMessage(@NotNull PartitionKey partitionKey,
                                        @NotNull Throwable cause) {

  }

  // Ensured by onStart() before any message is processed
  @SuppressWarnings("NotNullFieldNotInitialized")
  private @NotNull EventStore<E> eventStore;

  private @NotNull List<E> events;

  protected PersistentActor() {
    this.events = new ArrayList<>();
  }

  @Override
  public final void onStart() {

    final var partitionKey = partitionKey();

    this.eventStore = PersistenceExtensionHolder.getInstance().require()
        .<E>resolveStore(partitionKey).orElseThrow(() -> new IllegalStateException(
            "Failed to resolve store for partition key '%s'".formatted(partitionKey.value())));

    this.events = new ArrayList<>();

    behaveAs(this::recoveringBehaviour);

    pipeFuture(this.eventStore.loadEvents(partitionKey).thenApply(events -> {
      //noinspection unchecked
      return (M) new RecoveryResultMessage<>(events);
    }).exceptionally(cause -> {
      //noinspection unchecked
      return (M) new RecoveryFailureMessage(partitionKey, cause);
    })).to(self());
  }

  @SuppressWarnings("unchecked")
  private void recoveringBehaviour(final @NotNull M message) {

    if (message instanceof RecoveryResultMessage<?> result) {

      this.events = new ArrayList<>(((RecoveryResultMessage<E>) result).events());

      behaveAsDefault();
      afterRecovery();

    } else if (message instanceof RecoveryFailureMessage(PartitionKey id, Throwable cause)) {
      throw new RecoveryFailedException(id, cause);
    } else {
      reject(message);
    }
  }

  protected final void persist(final @NotNull E event) {
    persistMultiple(List.of(event));
  }

  protected final void persistMultiple(final @NotNull List<E> events) {

    final var partitionKey = partitionKey();

    try {
      this.eventStore.appendMultiple(partitionKey, events).join();
      this.events.addAll(events);
    } catch (final Exception cause) {
      throw new SaveFailedException(partitionKey, cause);
    }
  }

  protected abstract @NotNull PartitionKey partitionKey();

  protected void afterRecovery() {
    // no-op by default
  }

  protected final @NotNull List<E> events() {
    return Collections.unmodifiableList(events);
  }
}
