package de.dangoe.concurrent.slact.persistence;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an envelope for an event, containing the event itself along with its metadata such as
 * ordering and timestamp.
 *
 * @param ordering  The ordering of the event, which can be used to maintain the sequence of
 *                  events.
 * @param timestamp The timestamp indicating when the event was created or recorded.
 * @param event     The actual event data of type <code>E</code>.
 * @param <E>       The type of the event contained in the envelope.
 */
public record EventEnvelope<E>(long ordering, @NotNull Instant timestamp, @NotNull E event) {

}
