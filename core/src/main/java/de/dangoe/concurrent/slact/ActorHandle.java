package de.dangoe.concurrent.slact;

import java.io.Serializable;

public interface ActorHandle<M extends Serializable> extends ActorFactory {

    ActorPath path();

    void send(M message, ActorHandle<?> sender);
}
