package de.dangoe.concurrent.slact.persistence;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a standard persisted event with its ordering, timestamp, and event payload. The
 * ordering is the position of the event within its partition key (e.g., an actor's event stream).
 *
 * @param ordering  The event's position within its partition.
 * @param timestamp The time the event was persisted.
 * @param event     The actual event payload.
 */
public record EventEnvelope<E>(long ordering, @NotNull Instant timestamp,
                               @NotNull E event) implements EventLogEntryLike {

}
