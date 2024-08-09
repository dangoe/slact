package de.dangoe.concurrent.slact;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ActorCreator<A> {

  @NotNull
  A create();
}
