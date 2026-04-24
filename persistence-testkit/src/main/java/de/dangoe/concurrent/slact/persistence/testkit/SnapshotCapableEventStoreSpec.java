// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.CompletionException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Abstract specification for the
 * {@link de.dangoe.concurrent.slact.persistence.SnapshotCapableEventStore} contract, extending
 * {@link EventStoreSpec} with snapshot-specific test cases.
 */
@DisplayName("Snapshot-capable event store")
public abstract class SnapshotCapableEventStoreSpec extends EventStoreSpec {

  /**
   * A simple test snapshot holding a string value.
   *
   * @param value the value contained in this snapshot.
   */
  protected record TestSnapshot(String value) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
  }

  private static final PartitionKey PARTITION_A = new PartitionKey("test", "snapshot-partition-a");
  private static final PartitionKey PARTITION_B = new PartitionKey("test", "snapshot-partition-b");

  private SnapshotCapableEventStore snapshotStore;

  /**
   * Creates a fresh {@link SnapshotCapableEventStore} backed by the concrete infrastructure. Called
   * once per test after {@link #cleanDatabase()}.
   *
   * @return a new {@link SnapshotCapableEventStore} backed by the test infrastructure.
   */
  protected abstract @NotNull SnapshotCapableEventStore createSnapshotCapableEventStore();

  /**
   * Seeds a snapshot directly into the underlying store (e.g. via a raw SQL insert) without going
   * through the {@link SnapshotCapableEventStore} API. This lets the read-path tests work
   * independently of any future {@code saveSnapshot} implementation. Implementations must also
   * insert the corresponding snapshot marker event into the {@code events} table so that the
   * marker-filter contract of {@link EventStore#loadEvents} can be verified.
   *
   * @param key                 the partition key to seed the snapshot for.
   * @param ordering            the ordering value for the snapshot entry.
   * @param appliedUpToOrdering the ordering of the last event applied before this snapshot.
   * @param snapshot            the snapshot state to seed.
   * @throws Exception if the seed operation fails.
   */
  protected abstract void seedSnapshot(@NotNull PartitionKey key, long ordering,
      long appliedUpToOrdering, @NotNull TestSnapshot snapshot) throws Exception;

  @Override
  protected final @NotNull EventStore createEventStore() {
    return createSnapshotCapableEventStore();
  }

  @BeforeEach
  final void setUpSnapshotStore() {
    snapshotStore = createSnapshotCapableEventStore();
  }

  @Test
  @DisplayName("Loading the latest snapshot from an empty partition returns an empty optional")
  void loadLatestSnapshotFromEmptyPartitionReturnsEmptyOptional() {
    final var result = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();

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
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();

      assertThat(result).isPresent();
      assertThat(result.get().snapshot()).isEqualTo(SNAPSHOT);
    }

    @Test
    @DisplayName("The returned envelope has ordering equal to the seeded ordering")
    void theReturnedEnvelopeHasCorrectOrdering() {
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();

      assertThat(result).isPresent();
      assertThat(result.get().ordering()).isEqualTo(SNAPSHOT_ORDERING);
    }

    @Test
    @DisplayName("The returned envelope has appliedUpToOrdering equal to the seeded value")
    void theReturnedEnvelopeHasCorrectAppliedUpToOrdering() {
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();

      assertThat(result).isPresent();
      assertThat(result.get().appliedUpToOrdering()).isEqualTo(APPLIED_UP_TO_ORDERING);
    }

    @Test
    @DisplayName("The returned envelope has a non-null timestamp")
    void theReturnedEnvelopeHasTimestamp() {
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();

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
      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();

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
      final var resultA = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();
      final var resultB = snapshotStore.loadLatestSnapshot(PARTITION_B, TestSnapshot.class).join();

      assertThat(resultA).isPresent();
      assertThat(resultA.get().snapshot()).isEqualTo(new TestSnapshot("state-a"));

      assertThat(resultB).isPresent();
      assertThat(resultB.get().snapshot()).isEqualTo(new TestSnapshot("state-b"));
    }
  }

  @Nested
  @DisplayName("Given a saved snapshot")
  class GivenASavedSnapshot {

    private static final TestSnapshot SNAPSHOT = new TestSnapshot("state-v1");
    private static final long LAST_SNAPSHOT_ORDERING = -1L;
    private static final long APPLIED_UP_TO_ORDERING = 3L;

    @Test
    @DisplayName("The returned envelope has ordering equal to lastSnapshotOrdering + 1")
    void theReturnedEnvelopeHasCorrectOrdering() {
      final var envelope = snapshotStore.saveSnapshot(PARTITION_A, LAST_SNAPSHOT_ORDERING,
          APPLIED_UP_TO_ORDERING, SNAPSHOT).join();

      assertThat(envelope.ordering()).isEqualTo(LAST_SNAPSHOT_ORDERING + 1);
    }

    @Test
    @DisplayName("The returned envelope has a non-null timestamp")
    void theReturnedEnvelopeHasTimestamp() {
      final var envelope = snapshotStore.saveSnapshot(PARTITION_A, LAST_SNAPSHOT_ORDERING,
          APPLIED_UP_TO_ORDERING, SNAPSHOT).join();

      assertThat(envelope.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("The returned envelope has appliedUpToOrdering equal to the input value")
    void theReturnedEnvelopeHasCorrectAppliedUpToOrdering() {
      final var envelope = snapshotStore.saveSnapshot(PARTITION_A, LAST_SNAPSHOT_ORDERING,
          APPLIED_UP_TO_ORDERING, SNAPSHOT).join();

      assertThat(envelope.appliedUpToOrdering()).isEqualTo(APPLIED_UP_TO_ORDERING);
    }

    @Test
    @DisplayName("The returned envelope contains the saved snapshot")
    void theReturnedEnvelopeContainsTheSavedSnapshot() {
      final var envelope = snapshotStore.saveSnapshot(PARTITION_A, LAST_SNAPSHOT_ORDERING,
          APPLIED_UP_TO_ORDERING, SNAPSHOT).join();

      assertThat(envelope.snapshot()).isEqualTo(SNAPSHOT);
    }

    @Test
    @DisplayName("The saved snapshot can be loaded back from the partition")
    void theSavedSnapshotCanBeLoadedBack() {
      snapshotStore.saveSnapshot(PARTITION_A, LAST_SNAPSHOT_ORDERING, APPLIED_UP_TO_ORDERING,
          SNAPSHOT).join();

      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();

      assertThat(result).isPresent();
      assertThat(result.get().snapshot()).isEqualTo(SNAPSHOT);
    }

    @Test
    @DisplayName("The snapshot marker event is not included when loading events")
    void snapshotMarkerIsNotIncludedInLoadedEvents() {
      snapshotStore.saveSnapshot(PARTITION_A, LAST_SNAPSHOT_ORDERING, APPLIED_UP_TO_ORDERING,
          SNAPSHOT).join();

      final var events = snapshotStore.loadEvents(PARTITION_A).join();

      assertThat(events).isEmpty();
    }
  }

  @Nested
  @DisplayName("Given multiple saved snapshots")
  class GivenMultipleSavedSnapshots {

    @Test
    @DisplayName("Loading the latest snapshot returns the one with the highest ordering")
    void loadLatestSnapshotReturnsTheMostRecentOne() {
      snapshotStore.saveSnapshot(PARTITION_A, -1L, 1L, new TestSnapshot("state-v1")).join();
      final var second = snapshotStore.saveSnapshot(PARTITION_A, 0L, 3L,
          new TestSnapshot("state-v2")).join();

      final var result = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();

      assertThat(result).isPresent();
      assertThat(result.get().ordering()).isEqualTo(second.ordering());
      assertThat(result.get().snapshot()).isEqualTo(new TestSnapshot("state-v2"));
    }
  }

  @Nested
  @DisplayName("Given saved snapshots in two separate partitions")
  class GivenSavedSnapshotsInTwoSeparatePartitions {

    @Test
    @DisplayName("Loading one partition does not return the snapshot from the other partition")
    void loadingOnePartitionDoesNotReturnSnapshotFromAnother() {
      snapshotStore.saveSnapshot(PARTITION_A, -1L, 1L, new TestSnapshot("state-a")).join();
      snapshotStore.saveSnapshot(PARTITION_B, -1L, 2L, new TestSnapshot("state-b")).join();

      final var resultA = snapshotStore.loadLatestSnapshot(PARTITION_A, TestSnapshot.class).join();
      final var resultB = snapshotStore.loadLatestSnapshot(PARTITION_B, TestSnapshot.class).join();

      assertThat(resultA).isPresent();
      assertThat(resultA.get().snapshot()).isEqualTo(new TestSnapshot("state-a"));

      assertThat(resultB).isPresent();
      assertThat(resultB.get().snapshot()).isEqualTo(new TestSnapshot("state-b"));
    }
  }

  @Nested
  @DisplayName("Given a concurrent snapshot write attempt")
  class GivenAConcurrentSnapshotWriteAttempt {

    @Test
    @DisplayName("Saving a snapshot with a stale lastSnapshotOrdering throws ConcurrentWriteException")
    void savingSnapshotWithStaleOrderingThrowsConcurrentWriteException() {
      snapshotStore.saveSnapshot(PARTITION_A, null, 1L, new TestSnapshot("state-v1")).join();

      final var thrown = catchThrowable(
          () -> snapshotStore.saveSnapshot(PARTITION_A, 0L, 2L, new TestSnapshot("state-v2"))
              .join());
      final Throwable actual = thrown instanceof CompletionException ce ? ce.getCause() : thrown;
      assertThat(actual).isInstanceOf(ConcurrentWriteException.class);
    }
  }

  /**
   * Creates a new spec instance.
   */
  protected SnapshotCapableEventStoreSpec() {
    super();
  }
}
