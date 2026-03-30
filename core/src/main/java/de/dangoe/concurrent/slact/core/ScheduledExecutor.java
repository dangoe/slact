package de.dangoe.concurrent.slact.core;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Executor for scheduling tasks with delays or at fixed intervals.
 * <p>
 * Provides methods to schedule tasks and manage their lifecycle.
 * </p>
 */
public interface ScheduledExecutor extends AutoCloseable {

  /**
   * Schedules a one-time task to run after the specified delay.
   *
   * @param command      the task to run.
   * @param initialDelay the delay before execution.
   * @return a {@link Cancellable} handle to cancel the task.
   */
  @NotNull
  Cancellable scheduleOnce(@NotNull Runnable command, @NotNull Duration initialDelay);

  /**
   * Schedules a task to run at a fixed rate after an initial delay.
   *
   * @param command      the task to run.
   * @param initialDelay the delay before first execution.
   * @param period       the interval between executions.
   * @return a {@link Cancellable} handle to cancel the task.
   */
  @NotNull
  Cancellable scheduleAtFixedRate(@NotNull Runnable command, @NotNull Duration initialDelay,
      @NotNull Duration period);

  /**
   * Creates a ScheduledExecutor backed by a fixed thread pool.
   *
   * @param poolSize the number of threads in the pool.
   * @return a new {@link ScheduledExecutor} instance.
   */
  static ScheduledExecutor withFixedThreadPool(final int poolSize) {

    return new ScheduledExecutor() {

      private final ScheduledExecutorService delegate = Executors.newScheduledThreadPool(poolSize);

      @Override
      public @NotNull Cancellable scheduleOnce(final @NotNull Runnable command,
          final @NotNull Duration initialDelay) {

        final var scheduledFuture = this.delegate.schedule(command, initialDelay.toNanos(),
            TimeUnit.NANOSECONDS);

        return () -> scheduledFuture.cancel(true);
      }

      @Override
      public @NotNull Cancellable scheduleAtFixedRate(final @NotNull Runnable command,
          final @NotNull Duration initialDelay,
          final @NotNull Duration period) {

        final var scheduledFuture = this.delegate.scheduleAtFixedRate(command,
            initialDelay.toNanos(), period.toNanos(), TimeUnit.NANOSECONDS);

        return () -> scheduledFuture.cancel(true);
      }

      @Override
      public void close() throws Exception {
        this.delegate.close();
      }
    };
  }
}
