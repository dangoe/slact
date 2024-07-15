package de.dangoe.concurrent.slact;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

interface ScheduledExecutor {

  ScheduledFuture<?> scheduleOnce(Runnable command, Duration initialDelay);

  ScheduledFuture<?> scheduleAtFixedRate(Runnable command, Duration initialDelay, Duration period);
}
