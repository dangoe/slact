package de.dangoe.concurrent.slact;

import java.time.Duration;

interface ScheduledExecutor {

  void scheduleOnce(Runnable command, Duration initialDelay);

  void scheduleAtFixedRate(Runnable command, Duration initialDelay, Duration period);
}
