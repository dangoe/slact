package de.dangoe.concurrent.slact;

import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public final class SlactContainerBuilder {

  private Supplier<ScheduledExecutor> scheduledExecutorFactory;

  public SlactContainerBuilder() {
    this.scheduledExecutorFactory = () -> ScheduledExecutor.withFixedThreadPool(4);
  }

  public SlactContainerBuilder withScheduledExecutorFactory(
      final @NotNull Supplier<ScheduledExecutor> scheduledExecutorFactory) {
    this.scheduledExecutorFactory = scheduledExecutorFactory;
    return this;
  }

  public @NotNull SlactContainer build() {
    return new DefaultSlactContainer(this.scheduledExecutorFactory);
  }
}
