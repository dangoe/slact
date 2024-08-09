package de.dangoe.concurrent.slact;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class SlactContainerBuilder {

  private String name;
  private Supplier<ScheduledExecutor> scheduledExecutorFactory;

  public SlactContainerBuilder() {
    this.name = UUID.randomUUID().toString();
    this.scheduledExecutorFactory = () -> ScheduledExecutor.withFixedThreadPool(4);
  }

  public SlactContainerBuilder withName(final String name) {
    Objects.requireNonNull(name, "Custom name must not be null!");
    this.name = name;
    return this;
  }

  public SlactContainerBuilder withScheduledExecutorFactory(
      final Supplier<ScheduledExecutor> scheduledExecutorFactory) {
    Objects.requireNonNull(name, "Scheduled executor factory must not be null!");
    this.scheduledExecutorFactory = scheduledExecutorFactory;
    return this;
  }

  public SlactContainer build() {
    return new DefaultSlactContainer(this.name, this.scheduledExecutorFactory);
  }
}
