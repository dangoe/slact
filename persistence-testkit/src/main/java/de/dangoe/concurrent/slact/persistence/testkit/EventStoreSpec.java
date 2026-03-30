package de.dangoe.concurrent.slact.persistence.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import de.dangoe.concurrent.slact.persistence.EventEnvelope;
import de.dangoe.concurrent.slact.persistence.EventStore;
import de.dangoe.concurrent.slact.persistence.PartitionKey;
import de.dangoe.concurrent.slact.persistence.exception.ConcurrentWriteException;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletionException;
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

  /**
   * A simple test event holding a string value.
   *
   * @param value the value contained in this event.
   */
  public record TestEvent(String value) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
  }

  private static final PartitionKey PARTITION_A = new PartitionKey("test", "partition-a");
  private static final PartitionKey PARTITION_B = new PartitionKey("test", "partition-b");

  private EventStore eventStore;

  /**
   * Creates a fresh {@link EventStore} instance backed by the concrete infrastructure. Called once
   * per test after {@link #cleanDatabase()}.
   *
   * @return a new {@link EventStore} backed by the test infrastructure.
   */
  protected abstract @NotNull EventStore createEventStore();

  /**
   * Resets the database to a clean state before each test so that ordering counters and stored
   * events do not bleed across tests.
   *
   * @throws Exception if the database cleanup fails.
   */
  protected abstract void cleanDatabase() throws Exception;

  /**
   * Creates a new spec instance.
   */
  protected EventStoreSpec() {
    super();
  }

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

    private static final List<TestEvent> EVENTS = List.of(new TestEvent("first"),
        new TestEvent("second"), new TestEvent("third"));

    @Test
    @DisplayName("All events are loaded back in insertion order")
    void allEventsAreLoadedInInsertionOrder() {
      eventStore.appendMultiple(PARTITION_A, -1L, EVENTS).join();

      final var loaded = eventStore.<TestEvent>loadEvents(PARTITION_A).join();

      assertThat(loaded).hasSize(3);
      assertThat(loaded.stream().map(e -> e.event().value()).toList()).containsExactly("first",
          "second", "third");
    }

    @Test
    @DisplayName("Events have sequential orderings starting from lastMaxOrdering + 1")
    void eventsHaveSequentialOrderings() {
      final long lastMaxOrdering = -1L;
      final var envelopes = eventStore.appendMultiple(PARTITION_A, lastMaxOrdering, EVENTS).join();

      assertThat(envelopes).hasSize(3);
      assertThat(envelopes.stream().mapToLong(EventEnvelope::ordering).toArray()).containsExactly(
          lastMaxOrdering + 1, lastMaxOrdering + 2, lastMaxOrdering + 3);
    }

    @Test
    @DisplayName("Appending an empty list returns an empty list")
    void appendingAnEmptyListReturnsAnEmptyList() {
      final var result = eventStore.appendMultiple(PARTITION_A, -1L, List.of()).join();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Given a fromOrdering filter")
  class GivenAFromOrderingFilter {

    @Test
    @DisplayName("Only events with ordering greater than or equal to fromOrdering are returned")
    void onlyEventsAtOrAfterFromOrderingAreReturned() {
      final var envelopes = eventStore.appendMultiple(PARTITION_A, -1L,
          List.of(new TestEvent("e0"), new TestEvent("e1"), new TestEvent("e2"))).join();

      final long secondOrdering = envelopes.get(1).ordering();
      final var loaded = eventStore.<TestEvent>loadEvents(PARTITION_A, secondOrdering).join();

      assertThat(loaded).hasSize(2);
      assertThat(loaded.stream().map(e -> e.event().value()).toList()).containsExactly("e1", "e2");
    }

    @Test
    @DisplayName("Loading events with a fromOrdering beyond all stored events returns an empty list")
    void loadEventsWithFromOrderingBeyondAllEventsReturnsEmptyList() {
      final var envelope = eventStore.append(PARTITION_A, -1L, new TestEvent("only")).join();

      final var loaded = eventStore.loadEvents(PARTITION_A, envelope.ordering() + 1).join();

      assertThat(loaded).isEmpty();
    }
  }

  @Nested
  @DisplayName("Given events in two separate partitions")
  class GivenEventsInTwoSeparatePartitions {

    @Test
    @DisplayName("Loading one partition does not return events from the other partition")
    void loadingOnePartitionDoesNotReturnEventsFromAnother() {

      eventStore.appendMultiple(PARTITION_A, -1L, List.of(new TestEvent("a1"), new TestEvent("a2")))
          .join();
      eventStore.appendMultiple(PARTITION_B, -1L, List.of(new TestEvent("b1"))).join();

      final var loadedA = eventStore.<TestEvent>loadEvents(PARTITION_A).join();
      final var loadedB = eventStore.<TestEvent>loadEvents(PARTITION_B).join();

      assertThat(loadedA).hasSize(2);
      assertThat(loadedA.stream().map(e -> e.event().value()).toList()).containsExactly("a1", "a2");

      assertThat(loadedB).hasSize(1);
      assertThat(loadedB.getFirst().event().value()).isEqualTo("b1");
    }
  }

  @Nested
  @DisplayName("Given a concurrent write attempt")
  class GivenAConcurrentWriteAttempt {

    @Test
    @DisplayName("Appending with a stale lastMaxOrdering throws ConcurrentWriteException")
    void appendingWithStaleOrderingThrowsConcurrentWriteException() {
      eventStore.append(PARTITION_A, -1L, new TestEvent("first")).join();

      final var thrown = catchThrowable(
          () -> eventStore.append(PARTITION_A, -1L, new TestEvent("concurrent")).join());
      final Throwable actual = thrown instanceof CompletionException ce ? ce.getCause() : thrown;
      assertThat(actual).isInstanceOf(ConcurrentWriteException.class);
    }
  }
}
