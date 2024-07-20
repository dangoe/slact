package de.dangoe.concurrent.slact;

@FunctionalInterface
public interface FuturePipeOp<M> {

  void to(ActorHandle<M> target);
}
