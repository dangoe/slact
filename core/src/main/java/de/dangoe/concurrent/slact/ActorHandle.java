package de.dangoe.concurrent.slact;

import java.io.Serializable;

public interface ActorHandle<M extends Serializable> extends ActorRegistry {

    ActorPath path();

    void send(M message, ActorHandle<?> sender);

    void forward(M message, ActorContext context);
}
