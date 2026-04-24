// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core;

import de.dangoe.concurrent.slact.core.internal.DefaultSlactContainer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * Builder for creating a {@link SlactContainer}. Use {@link #withScheduledExecutorFactory} to
 * override the default 16-thread pool.
 * <pre>{@code
 * try (SlactContainer container = new SlactContainerBuilder().build()) {
 *     ActorHandle<String> greeter = container.spawn("greeter", GreeterActor::new);
 *     container.send("World").to(greeter);
 * }
 * }</pre>
 */
public final class SlactContainerBuilder {

  private Supplier<ScheduledExecutor> scheduledExecutorFactory;

  /**
   * Creates a builder with a default 16-thread scheduled executor.
   */
  public SlactContainerBuilder() {
    this.scheduledExecutorFactory = () -> ScheduledExecutor.withFixedThreadPool(16);
  }

  /**
   * Overrides the factory used to create the container's {@link ScheduledExecutor}.
   *
   * @param scheduledExecutorFactory supplier that produces the executor on each {@link #build()}
   *                                 call.
   * @return this builder.
   */
  public SlactContainerBuilder withScheduledExecutorFactory(
      final @NotNull Supplier<ScheduledExecutor> scheduledExecutorFactory) {
    this.scheduledExecutorFactory = scheduledExecutorFactory;
    return this;
  }

  /**
   * Builds and returns a new {@link SlactContainer} using the current builder configuration.
   *
   * @return a fully initialised {@link SlactContainer}.
   */
  public @NotNull SlactContainer build() {
    return new DefaultSlactContainer(this.scheduledExecutorFactory);
  }
}
