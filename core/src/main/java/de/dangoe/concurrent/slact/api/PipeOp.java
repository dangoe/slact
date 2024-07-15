package de.dangoe.concurrent.slact.api;

@FunctionalInterface
public interface PipeOp<M> {

  void to(ActorHandle<M> target);
}
