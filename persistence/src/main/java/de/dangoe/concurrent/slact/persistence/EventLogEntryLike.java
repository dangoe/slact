package de.dangoe.concurrent.slact.persistence;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

public interface EventLogEntryLike {

  long ordering();

  @NotNull Instant timestamp();
}
