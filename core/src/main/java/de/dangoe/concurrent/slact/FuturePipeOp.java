package de.dangoe.concurrent.slact;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface FuturePipeOp<M> {

  void to(@NotNull ActorHandle<M> target);
}
