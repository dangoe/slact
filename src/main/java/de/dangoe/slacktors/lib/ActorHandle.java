package de.dangoe.slacktors.lib;

public interface ActorHandle<M> extends ActorFactory {
    ActorPath path();
    void send(M message, ActorHandle<?> sender);
}
