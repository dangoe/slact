package de.dangoe.concurrent.slact.persistence;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

public interface EventStore<E> {

  @NotNull CompletableFuture<List<E>> loadEvents(@NotNull PartitionKey partitionKey);

  default @NotNull CompletableFuture<Void> append(@NotNull PartitionKey partitionKey,
      @NotNull E event) {

    return appendMultiple(partitionKey, List.of(event));
  }

  @NotNull CompletableFuture<Void> appendMultiple(@NotNull PartitionKey partitionKey,
      @NotNull List<E> events);
}
