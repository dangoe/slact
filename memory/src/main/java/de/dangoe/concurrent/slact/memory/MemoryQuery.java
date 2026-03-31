package de.dangoe.concurrent.slact.memory;

import org.jetbrains.annotations.NotNull;

/**
 * A query for memories similar to the given embedding vector.
 *
 * @param embedding the query embedding vector.
 * @param topK      maximum number of results to return.
 */
public record MemoryQuery(float @NotNull [] embedding, int topK) {}
