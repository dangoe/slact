package de.dangoe.concurrent.slact.persistence.storage.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore;
import java.io.Serial;
import java.io.Serializable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Abstract specification for the {@link SnapshotCapableEventStore} contract. Extends
 * {@link EventStoreSpec} so that all event-store behavioural tests are inherited and re-run for
 * every {@link JdbcDialect} implementation that supports snapshots.
 *
 * <p>Subclasses provide the concrete infrastructure via
 * {@link #createSnapshotCapableEventStore()} and seed snapshot data for read-path tests via
 * {@link #seedSnapshot(PartitionKey, long, TestSnapshot)}, keeping the spec independent of any
 * future {@code saveSnapshot} API.
 */
@DisplayName("Snapshot-capable event store")
public abstract class SnapshotCapableEventStoreSpec extends EventStoreSpec {

  protected record TestSnapshot(String value) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
  }

  private static final PartitionKey PARTITION_A = PartitionKey.of("snapshot-partition-a");
  private static final PartitionKey PARTITION_B = PartitionKey.of("snapshot-partition-b");

  private SnapshotCapableEventStore<TestEvent, TestSnapshot> snapshotStore;

  /**
   * Creates a fresh {@link SnapshotCapableEventStore} backed by the concrete infrastructure.
   * Called once per test after {@link #cleanDatabase()}.
   */
  protected abstract @NotNull SnapshotCapableEventStore<TestEvent, TestSnapshot> createSnapshotCapableEventStore();

  /**
   * Seeds a snapshot directly into the underlying store (e.g. via a raw SQL insert) without going
   * through the {@link SnapshotCapableEventStore} API. This lets the read-path tests work
   * independently of any future {@code saveSnapshot} implementation. Implementations must also
   * insert the corresponding snapshot marker event into the {@code events} table so that the
   * marker-filter contract of {@link EventStore#loadEvents} can be verified.
   */
  protected abstract void seedSnapshot(@NotNull PartitionKey key, long ordering,
      long appliedUpToOrdering, @NotNull TestSnapshot snapshot) throws Exception;

  @Override
  protected final @NotNull EventStore<TestEvent> createEventStore() {
    return createSnapshotCapableEventStore();
  }

  @BeforeEach
  final void setUpSnapshotStore() throws Exception {
    snapshotStore = createSnapshotCapableEventStore();
  }

  @Test
  @DisplayName("Loading the latest snapshot from an empty partition returns an empty optional")
  void loadLatestSnapshotFromEmptyPartitionReturnsEmptyOptional() {
    final var result = snapshotStore.loadLatestSnapshot(PARTITION_A).join();

    assertThat(result).isEmpty();
  }

  @Nested
  @DisplayName("Given a single seeded snapshot")
  class GivenASingleSeededSnapshot {

    private static final TestSnapshot SNAPSHOT = new TestSnapshot("state-v1");
    private static final long SNAPSHOT_ORDERING = 2L;
    private static final long APPLIED_UP_TO_ORDERING = 1L;

    @BeforeEach
    void seedData() throws Exception {
      seedSnapshot(PARTITION_A, SNAPSHOT_ORDERING, APPLIED_UP_TO_ORDERING, SNAPSHOT);
    }

    @Test
    @DisplayName("The snapshot can be loaded back from the partition")
    void theSnapshotCanBeLoadedBack() {
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A).join();

      assertThat(result).isPresent();
      assertThat(result.get().snapshot()).isEqualTo(SNAPSHOT);
    }

    @Test
    @DisplayName("The returned envelope has ordering equal to the seeded ordering")
    void theReturnedEnvelopeHasCorrectOrdering() {
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A).join();

      assertThat(result).isPresent();
      assertThat(result.get().ordering()).isEqualTo(SNAPSHOT_ORDERING);
    }

    @Test
    @DisplayName("The returned envelope has appliedUpToOrdering equal to the seeded value")
    void theReturnedEnvelopeHasCorrectAppliedUpToOrdering() {
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A).join();

      assertThat(result).isPresent();
      assertThat(result.get().appliedUpToOrdering()).isEqualTo(APPLIED_UP_TO_ORDERING);
    }

    @Test
    @DisplayName("The returned envelope has a non-null timestamp")
    void theReturnedEnvelopeHasTimestamp() {
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A).join();

      assertThat(result).isPresent();
      assertThat(result.get().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("The snapshot marker event is not included when loading events")
    void snapshotMarkerIsNotIncludedInLoadedEvents() {
      final var events = snapshotStore.loadEvents(PARTITION_A).join();

      assertThat(events).isEmpty();
    }
  }

  @Nested
  @DisplayName("Given multiple seeded snapshots")
  class GivenMultipleSeededSnapshots {

    @BeforeEach
    void seedData() throws Exception {
      seedSnapshot(PARTITION_A, 2L, 1L, new TestSnapshot("state-v1"));
      seedSnapshot(PARTITION_A, 5L, 4L, new TestSnapshot("state-v2"));
    }

    @Test
    @DisplayName("Loading the latest snapshot returns the one with the highest ordering")
    void loadLatestSnapshotReturnsTheMostRecentOne() {
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A).join();

      assertThat(result).isPresent();
      assertThat(result.get().ordering()).isEqualTo(5L);
      assertThat(result.get().snapshot()).isEqualTo(new TestSnapshot("state-v2"));
    }
  }

  @Nested
  @DisplayName("Given snapshots in two separate partitions")
  class GivenSnapshotsInTwoSeparatePartitions {

    @BeforeEach
    void seedData() throws Exception {
      seedSnapshot(PARTITION_A, 2L, 1L, new TestSnapshot("state-a"));
      seedSnapshot(PARTITION_B, 3L, 2L, new TestSnapshot("state-b"));
    }

    @Test
    @DisplayName("Loading one partition does not return the snapshot from the other partition")
    void loadingOnePartitionDoesNotReturnSnapshotFromAnother() {
      final var resultA = snapshotStore.loadLatestSnapshot(PARTITION_A).join();
      final var resultB = snapshotStore.loadLatestSnapshot(PARTITION_B).join();

      assertThat(resultA).isPresent();
      assertThat(resultA.get().snapshot()).isEqualTo(new TestSnapshot("state-a"));

      assertThat(resultB).isPresent();
      assertThat(resultB.get().snapshot()).isEqualTo(new TestSnapshot("state-b"));
    }
  }
}
