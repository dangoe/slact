package de.dangoe.concurrent.slact;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

interface ScheduledExecutor extends AutoCloseable {

  void scheduleOnce(Runnable command, Duration initialDelay);

  void scheduleAtFixedRate(Runnable command, Duration initialDelay, Duration period);

  static ScheduledExecutor withFixedThreadPool(final int poolSize) {

    return new ScheduledExecutor() {

      private final ScheduledExecutorService delegate = Executors.newScheduledThreadPool(
          poolSize);

      @Override
      public void scheduleOnce(final Runnable command, final Duration initialDelay) {
        this.delegate.schedule(command, initialDelay.toMillis(), TimeUnit.MILLISECONDS);
      }

      @Override
      public void scheduleAtFixedRate(final Runnable command, final Duration initialDelay,
          final Duration period) {
        this.delegate.scheduleAtFixedRate(command, initialDelay.toMillis(), period.toMillis(),
            TimeUnit.MILLISECONDS);
      }

      @Override
      public void close() throws Exception {
        this.delegate.close();
      }
    };
  }
}
