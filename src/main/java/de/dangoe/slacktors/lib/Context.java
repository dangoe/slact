package de.dangoe.slacktors.lib;

public interface Context {

    <A extends Actor<M>, M> ActorHandle<M> actorOf(Class<A> type);

    <A extends Actor<M>, M> ActorHandle<M> select(ActorPath path);
}
