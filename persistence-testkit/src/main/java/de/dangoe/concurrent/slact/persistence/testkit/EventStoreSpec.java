package de.dangoe.concurrent.slact.persistence.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Abstract specification for the {@link EventStore} contract. Subclasses provide the concrete
 * infrastructure (database, connection pool, etc.) via the two template methods.
 */
@DisplayName("Event store")
public abstract class EventStoreSpec {

  public record TestEvent(String value) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
  }

  private record TestEventPartitionKey(@NotNull String value) implements PartitionKey<TestEvent> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public @NotNull Class<TestEvent> eventType() {
      return TestEvent.class;
    }
  }

  private static final PartitionKey<TestEvent> PARTITION_A = new TestEventPartitionKey(
      "partition-a");
  private static final PartitionKey<TestEvent> PARTITION_B = new TestEventPartitionKey(
      "partition-b");

  private EventStore eventStore;

  /**
   * Creates a fresh {@link EventStore} instance backed by the concrete infrastructure. Called once
   * per test after {@link #cleanDatabase()}.
   */
  protected abstract @NotNull EventStore createEventStore();

  /**
   * Resets the database to a clean state before each test so that ordering counters and stored
   * events do not bleed across tests.
   */
  protected abstract void cleanDatabase() throws Exception;

  @BeforeEach
  final void setUpEventStore() throws Exception {
    cleanDatabase();
    eventStore = createEventStore();
  }

  @Test
  @DisplayName("Loading events from an empty partition returns an empty list")
  void loadEventsFromEmptyPartitionReturnsEmptyList() {
    final var events = eventStore.loadEvents(PARTITION_A).join();

    assertThat(events).isEmpty();
  }

  @Nested
  @DisplayName("Given a single appended event")
  class GivenASingleAppendedEvent {

    private static final TestEvent EVENT = new TestEvent("hello");
    private static final long INITIAL_ORDERING = -1L;

    @Test
    @DisplayName("The event can be loaded back from the partition")
    void theEventCanBeLoadedBack() {
      eventStore.append(PARTITION_A, INITIAL_ORDERING, EVENT).join();

      final var loaded = eventStore.loadEvents(PARTITION_A).join();

      assertThat(loaded).hasSize(1);
      assertThat(loaded.getFirst().event()).isEqualTo(EVENT);
    }

    @Test
    @DisplayName("The returned envelope has ordering equal to lastMaxOrdering + 1")
    void theReturnedEnvelopeHasCorrectOrdering() {
      final var envelope = eventStore.append(PARTITION_A, INITIAL_ORDERING, EVENT).join();

      assertThat(envelope.ordering()).isEqualTo(INITIAL_ORDERING + 1);
    }

    @Test
    @DisplayName("The returned envelope has a non-null timestamp")
    void theReturnedEnvelopeHasTimestamp() {
      final var envelope = eventStore.append(PARTITION_A, INITIAL_ORDERING, EVENT).join();

      assertThat(envelope.timestamp()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Given multiple appended events")
  class GivenMultipleAppendedEvents {

    private static final List<TestEvent> EVENTS = List.of(
        new TestEvent("first"),
        new TestEvent("second"),
        new TestEvent("third")
    );

    @Test
    @DisplayName("All events are loaded back in insertion order")
    void allEventsAreLoadedInInsertionOrder() {
      eventStore.appendMultiple(PARTITION_A, -1L, EVENTS).join();

      final var loaded = eventStore.loadEvents(PARTITION_A).join();

      assertThat(loaded).hasSize(3);
      assertThat(loaded.stream().map(e -> e.event().value()).toList())
          .containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("Events have sequential orderings starting from lastMaxOrdering + 1")
    void eventsHaveSequentialOrderings() {
      final long lastMaxOrdering = -1L;
      final var envelopes = eventStore.appendMultiple(PARTITION_A, lastMaxOrdering, EVENTS).join();

      assertThat(envelopes).hasSize(3);
      assertThat(envelopes.stream().mapToLong(e -> e.ordering()).toArray())
          .containsExactly(
              lastMaxOrdering + 1,
              lastMaxOrdering + 2,
              lastMaxOrdering + 3
          );
    }
  }

  @Nested
  @DisplayName("Given a fromOrdering filter")
  class GivenAFromOrderingFilter {

    @Test
    @DisplayName("Only events with ordering greater than or equal to fromOrdering are returned")
    void onlyEventsAtOrAfterFromOrderingAreReturned() {
      final var envelopes = eventStore.appendMultiple(PARTITION_A, -1L, List.of(
          new TestEvent("e0"),
          new TestEvent("e1"),
          new TestEvent("e2")
      )).join();

      final long secondOrdering = envelopes.get(1).ordering();
      final var loaded = eventStore.loadEvents(PARTITION_A, secondOrdering).join();

      assertThat(loaded).hasSize(2);
      assertThat(loaded.stream().map(e -> e.event().value()).toList())
          .containsExactly("e1", "e2");
    }
  }

  @Nested
  @DisplayName("Given events in two separate partitions")
  class GivenEventsInTwoSeparatePartitions {

    @Test
    @DisplayName("Loading one partition does not return events from the other partition")
    void loadingOnePartitionDoesNotReturnEventsFromAnother() {
      final var envelopesA = eventStore.appendMultiple(PARTITION_A, -1L,
          List.of(new TestEvent("a1"), new TestEvent("a2"))).join();

      eventStore.appendMultiple(PARTITION_B, -1L,
          List.of(new TestEvent("b1"))).join();

      final var loadedA = eventStore.loadEvents(PARTITION_A).join();
      final var loadedB = eventStore.loadEvents(PARTITION_B).join();

      assertThat(loadedA).hasSize(2);
      assertThat(loadedA.stream().map(e -> e.event().value()).toList())
          .containsExactly("a1", "a2");

      assertThat(loadedB).hasSize(1);
      assertThat(loadedB.getFirst().event().value()).isEqualTo("b1");
    }
  }
}
