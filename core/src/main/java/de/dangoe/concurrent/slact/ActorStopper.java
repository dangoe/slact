package de.dangoe.concurrent.slact;

import org.jetbrains.annotations.NotNull;

interface ActorStopper {

  void stop(@NotNull ActorPath path);
}
