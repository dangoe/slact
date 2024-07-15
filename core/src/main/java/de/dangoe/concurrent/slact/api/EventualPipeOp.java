package de.dangoe.concurrent.slact.api;

@FunctionalInterface
public interface EventualPipeOp<M> {

  void to(ActorHandle<M> target);
}
