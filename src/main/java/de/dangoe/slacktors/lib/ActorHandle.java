package de.dangoe.slacktors.lib;

import java.io.Serializable;

public interface ActorHandle<M extends Serializable> extends ActorFactory {

    ActorPath path();

    void send(M message, ActorHandle<?> sender);
}
