package de.dangoe.concurrent.slact;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public interface ScheduledExecutor extends AutoCloseable {

  Cancellable scheduleOnce(Runnable command, Duration initialDelay);

  Cancellable scheduleAtFixedRate(Runnable command, Duration initialDelay, Duration period);

  static ScheduledExecutor withFixedThreadPool(final int poolSize) {

    return new ScheduledExecutor() {

      private final ScheduledExecutorService delegate = Executors.newScheduledThreadPool(poolSize);

      @Override
      public Cancellable scheduleOnce(final Runnable command, final Duration initialDelay) {

        final var scheduledFuture = this.delegate.schedule(command, initialDelay.toMillis(),
            TimeUnit.MILLISECONDS);

        return () -> scheduledFuture.cancel(true);
      }

      @Override
      public Cancellable scheduleAtFixedRate(final Runnable command, final Duration initialDelay,
          final Duration period) {

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
