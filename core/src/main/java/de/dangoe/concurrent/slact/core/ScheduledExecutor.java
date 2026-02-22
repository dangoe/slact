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
   * @param command      The task to run.
   * @param initialDelay The delay before execution.
   * @return A {@link Cancellable} handle to cancel the task.
   */
  @NotNull
  Cancellable scheduleOnce(@NotNull Runnable command, @NotNull Duration initialDelay);

  /**
   * Schedules a task to run at a fixed rate after an initial delay.
   *
   * @param command      The task to run.
   * @param initialDelay The delay before first execution.
   * @param period       The interval between executions.
   * @return A {@link Cancellable} handle to cancel the task.
   */
  @NotNull
  Cancellable scheduleAtFixedRate(@NotNull Runnable command, @NotNull Duration initialDelay,
      @NotNull Duration period);

  /**
   * Creates a ScheduledExecutor backed by a fixed thread pool.
   *
   * @param poolSize The number of threads in the pool.
   * @return A new ScheduledExecutor instance.
   */
  static ScheduledExecutor withFixedThreadPool(final int poolSize) {

    return new ScheduledExecutor() {

      private final ScheduledExecutorService delegate = Executors.newScheduledThreadPool(poolSize);

      @Override
      public @NotNull Cancellable scheduleOnce(final @NotNull Runnable command,
          final @NotNull Duration initialDelay) {

        final var scheduledFuture = this.delegate.schedule(command, initialDelay.toMillis(),
            TimeUnit.MILLISECONDS);

        return () -> scheduledFuture.cancel(true);
      }

      @Override
      public @NotNull Cancellable scheduleAtFixedRate(final @NotNull Runnable command,
          final @NotNull Duration initialDelay,
          final @NotNull Duration period) {

        final var scheduledFuture = this.delegate.scheduleAtFixedRate(command,
            initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);

        return () -> scheduledFuture.cancel(true);
      }

      @Override
      public void close() throws Exception {
        this.delegate.close();
      }
    };
  }
}
