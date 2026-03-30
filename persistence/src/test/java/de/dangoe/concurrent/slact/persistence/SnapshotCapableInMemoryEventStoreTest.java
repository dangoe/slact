package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.testkit.SnapshotCapableEventStoreSpec;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;

/**
 * Runs the {@link SnapshotCapableEventStoreSpec} contract against the
 * {@link SnapshotCapableInMemoryEventStore} implementation.
 * <p>
 * This verifies, among other things, that the simplified {@code SnapshotStoreKey(raw)} record
 * correctly isolates snapshots across partitions with different {@link PartitionKey#raw()} values.
 * <p>
 * {@code seedSnapshot} bypasses the public API and writes directly into the private
 * {@code snapshots} map via reflection so that the read-path tests run independently of the
 * write-path implementation.
 */
@DisplayName("Snapshot-capable in-memory event store")
class SnapshotCapableInMemoryEventStoreTest extends SnapshotCapableEventStoreSpec {

  /**
   * Tracks the most-recently created store so that {@link #seedSnapshot} injects data into the same
   * instance that the spec's {@code snapshotStore} field holds.
   * <p>
   * Because {@link SnapshotCapableEventStoreSpec} calls {@code createSnapshotCapableEventStore()}
   * twice per test (once via {@code EventStoreSpec#setUpEventStore} and once via
   * {@code SnapshotCapableEventStoreSpec#setUpSnapshotStore}), the field is updated on every call
   * so it always reflects the last-returned instance — which is the one assigned to
   * {@code snapshotStore} in the spec.
   */
  private SnapshotCapableInMemoryEventStore currentStore;

  @Override
  protected @NotNull SnapshotCapableEventStore createSnapshotCapableEventStore() {
    currentStore = new SnapshotCapableInMemoryEventStore(Clock.systemUTC());
    return currentStore;
  }

  @Override
  protected void cleanDatabase() {
    // No-op: each test receives a brand-new SnapshotCapableInMemoryEventStore instance
  }

  /**
   * Seeds a snapshot directly into the private {@code snapshots} map of the current store via
   * reflection, honouring the caller-supplied {@code ordering} value without invoking the
   * {@code saveSnapshot} public API (which auto-generates orderings).
   */
  @Override
  @SuppressWarnings("unchecked")
  protected void seedSnapshot(final @NotNull PartitionKey key, final long ordering,
      final long appliedUpToOrdering, final @NotNull TestSnapshot snapshot) throws Exception {

    // Given: locate the private SnapshotStoreKey record class
    final Class<?> storeKeyClass = Arrays.stream(
            SnapshotCapableInMemoryEventStore.class.getDeclaredClasses())
        .filter(c -> c.getSimpleName().equals("SnapshotStoreKey"))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("SnapshotStoreKey inner class not found — "
                + "has the implementation changed?"));

    final var ctor = storeKeyClass.getDeclaredConstructor(String.class);
    ctor.setAccessible(true);
    final var storeKey = ctor.newInstance(key.raw());

    // When: obtain the snapshots map and insert the envelope directly
    final Field snapshotsField =
        SnapshotCapableInMemoryEventStore.class.getDeclaredField("snapshots");
    snapshotsField.setAccessible(true);
    final var snapshotsMap =
        (ConcurrentHashMap<Object, SnapshotEnvelope<?>>) snapshotsField.get(currentStore);

    snapshotsMap.put(storeKey,
        new SnapshotEnvelope<>(ordering, appliedUpToOrdering, Instant.now(), snapshot));
  }
}
