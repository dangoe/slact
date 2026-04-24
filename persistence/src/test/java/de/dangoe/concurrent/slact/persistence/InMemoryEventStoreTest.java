// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.testkit.EventStoreSpec;
import java.time.Clock;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;

/**
 * Runs the {@link EventStoreSpec} contract against the {@link InMemoryEventStore} implementation.
 * <p>
 * This verifies, among other things, that the simplified {@code StoreKey(raw)} record correctly
 * isolates events across partitions with different {@link PartitionKey#raw()} values.
 */
@DisplayName("In-memory event store")
class InMemoryEventStoreTest extends EventStoreSpec {

  @Override
  protected @NotNull EventStore createEventStore() {
    // Given: a fresh in-memory store — no shared state between tests
    return new InMemoryEventStore(Clock.systemUTC());
  }

  @Override
  protected void cleanDatabase() {
    // No-op: each test receives a brand-new InMemoryEventStore instance
  }
}
