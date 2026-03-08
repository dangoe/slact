package de.dangoe.concurrent.slact.persistence;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public interface PersistenceExtension {

  <S> @NotNull Optional<EventStore<S>> resolveStore(@NotNull PartitionKey key);
}
