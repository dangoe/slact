package de.dangoe.concurrent.slact;

@FunctionalInterface
public interface EventualPipeOp<M> {

  void to(ActorHandle<M> target);
}
