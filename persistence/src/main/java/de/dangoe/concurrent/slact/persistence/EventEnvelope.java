package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.EventEnvelope.DefaultEventEnvelope;
import de.dangoe.concurrent.slact.persistence.EventEnvelope.SnapshotEventEnvelope;
import de.dangoe.concurrent.slact.persistence.EventEnvelope.SnapshotMarkerEventEnvelope;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * An envelope type for persisted events, containing metadata such as ordering and timestamp, as
 * well as the event itself. Used to represent events when loading or storing them in the event
 * store. Supports three variants: standard events, compacted snapshot states, and snapshot marker
 * fences.
 *
 * @param <E> The type of the domain event payload.
 * @param <S> The type of the snapshot payload. Use {@link SnapshotPayload.None} for actors that do
 *            not support snapshotting.
 */
public sealed interface EventEnvelope<E, S extends SnapshotPayload> permits DefaultEventEnvelope,
    SnapshotEventEnvelope, SnapshotMarkerEventEnvelope {

  /**
   * Represents a standard persisted event with its ordering, timestamp, and event payload. The
   * ordering is the position of the event within its partition key (e.g., an actor's event
   * stream).
   *
   * @param ordering  The event's position within its partition.
   * @param timestamp The time the event was persisted.
   * @param event     The actual event payload.
   */
  record DefaultEventEnvelope<E, S extends SnapshotPayload>(long ordering, @NotNull Instant timestamp,
                                                      @NotNull E event) implements
      EventEnvelope<E, S> {

  }

  // ...existing code...
  record SnapshotEventEnvelope<E, S extends SnapshotPayload>(long ordering, @NotNull Instant timestamp,
                                                       @NotNull S snapshot) implements
      EventEnvelope<E, S> {

  }

  // ...existing code...
  record SnapshotMarkerEventEnvelope<E, S extends SnapshotPayload>(long ordering,
                                                             @NotNull Instant timestamp,
                                                             long snapshotEntryOrdering) implements
      EventEnvelope<E, S> {

  }

  long ordering();

  @NotNull Instant timestamp();
}
