package de.dangoe.concurrent.slact.memory.demo;

import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import de.dangoe.concurrent.slact.memory.TargetModelPort;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

final class StubTargetModelAdapter implements TargetModelPort {

  @Override
  public @NotNull RichFuture<String> complete(final @NotNull String prompt) {
    return RichFuture.of(CompletableFuture.completedFuture("Model reply: " + prompt));
  }
}
