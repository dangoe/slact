package de.dangoe.concurrent.slact.memory;

import org.jetbrains.annotations.NotNull;

/**
 * A query for memories similar to the given embedding vector.
 *
 * @param embedding  the query embedding vector.
 * @param maxResults maximum number of results to return.
 */
public record MemoryQuery(@NotNull Embedding embedding, int maxResults) {

}
