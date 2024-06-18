package de.dangoe.slacktors.lib;

public interface ActorHandle<M> {

    ActorPath path();

    void send(M message);
}
