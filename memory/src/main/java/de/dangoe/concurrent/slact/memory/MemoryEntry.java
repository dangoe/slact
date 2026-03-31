package de.dangoe.concurrent.slact.memory;

import org.jetbrains.annotations.NotNull;

/**
 * A memory entry returned from a similarity query, including the memory and its similarity score.
 *
 * @param memory the matched memory.
 * @param score  cosine similarity score in the range [0, 1].
 */
public record MemoryEntry(@NotNull Memory memory, @NotNull Score score) {}
