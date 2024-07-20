package de.dangoe.concurrent.slact.internal;

import java.time.Duration;

public interface ScheduledExecutor {

  void scheduleOnce(Runnable command, Duration initialDelay);

  void scheduleAtFixedRate(Runnable command, Duration initialDelay, Duration period);
}
