package de.dangoe.concurrent.slact.persistence;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a standard persisted event with its ordering, timestamp, and event snapshot. The
 * ordering is the position of the event within its partition key (e.g., an actor's event stream).
 *
 * @param ordering  the event's position within its partition.
 * @param timestamp the time the event was persisted.
 * @param event     the actual event snapshot.
 * @param <E>       the event type.
 */
public record EventEnvelope<E>(long ordering, @NotNull Instant timestamp,
                               @NotNull E event) implements EventLogEntryLike {

}
