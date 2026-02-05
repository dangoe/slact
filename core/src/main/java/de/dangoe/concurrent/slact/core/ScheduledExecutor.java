package de.dangoe.concurrent.slact.core;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public interface ScheduledExecutor extends AutoCloseable {

  @NotNull
  Cancellable scheduleOnce(@NotNull Runnable command, @NotNull Duration initialDelay);

  @NotNull
  Cancellable scheduleAtFixedRate(@NotNull Runnable command, @NotNull Duration initialDelay,
      @NotNull Duration period);

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
