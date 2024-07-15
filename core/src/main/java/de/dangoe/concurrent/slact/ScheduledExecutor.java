package de.dangoe.concurrent.slact;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

interface ScheduledExecutor {

  ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
      long initialDelay,
      long period,
      TimeUnit unit);
}
