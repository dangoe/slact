package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import org.jetbrains.annotations.NotNull;

interface ActorStopper {

  void stop(@NotNull ActorPath path);
}
