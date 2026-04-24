// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.persistence.PersistentActorBase.RecoveryData;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for actors whose state is fully derived from a persisted event log.
 * Subclasses define the partition key, handle messages, call {@link #persist} /
 * {@link #persistMultiple}, and optionally override {@link #afterRecovery} for post-recovery
 * initialization.
 *
 * <p>Example:
 * <pre>{@code
 * public class OrderActor extends PersistentActor<OrderCommand, OrderEvent> {
 *
 *     @Override
 *     protected PartitionKey partitionKey() {
 *         return PartitionKey.of("order-42");
 *     }
 *
 *     @Override
 *     public void onMessage(OrderCommand cmd) {
 *         persist(new OrderEvent(cmd));
 *     }
 *
 *     @Override
 *     protected void afterRecovery() {
 *         // rebuild in-memory state from events()
 *     }
 * }
 * }</pre>
 *
 * @param <M> the message (command) type.
 * @param <E> the event type.
 */
public abstract class PersistentActor<M, E> extends
    PersistentActorBase<M, E, RecoveryData<E>, EventStore> {

  /**
   * Creates a new persistent actor.
   */
  protected PersistentActor() {
    super();
  }

  @Override
  protected final RichFuture<RecoveryData<E>> loadRecoveryData(
      final @NotNull PartitionKey partitionKey) {
    return eventStore().<E>loadEvents(partitionKey).thenApply(it -> () -> it);
  }

  @Override
  protected final void recoverInternal(
      final @NotNull PersistentActorBase.RecoveryData<E> recoveryPayload) {

    // Nothing to do here, as the events are already loaded and can be accessed via the recovery snapshot.
  }

  @Override
  protected final @NotNull List<EventEnvelope<E>> events() {
    return super.events();
  }

  @Override
  protected final @NotNull EventStore eventStore() {
    return PersistenceExtensionHolder.getInstance().require().resolveStore(partitionKey())
        .orElseThrow(() -> new IllegalStateException(
            "Event store is not available for partition key '%s'".formatted(partitionKey().raw())));
  }

  @Override
  protected final void persistMultiple(final @NotNull List<E> events) {
    super.persistMultiple(events);
  }
}
