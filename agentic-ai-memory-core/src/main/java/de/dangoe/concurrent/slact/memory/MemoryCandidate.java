package de.dangoe.concurrent.slact.memory;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a candidate memory extracted from a prompt and response.
 *
 * @param subject the subject or topic of the memory
 * @param fact    the factual information associated with the subject
 */
public record MemoryCandidate(@NotNull String subject, @NotNull String fact) {

}
