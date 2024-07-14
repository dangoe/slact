package de.dangoe.slacktors.lib;

import java.io.Serializable;

public abstract class AbstractActor<M extends Serializable> {

    private ActorContext context;
    private ActorHandle<M> self;

    private ActorHandle<?> sender;

    final void onMessage(final M message, final ActorHandle<?> sender, final ActorHandle<M> self, final ActorContext context) {
        this.context = context;
        this.self = self;
        this.sender = sender;
        onMessage(message);
    }

    protected abstract void onMessage(M message);

    protected final ActorContext context() {
        return this.context;
    }

    protected final ActorHandle<M> self() {
        return this.self;
    }

    protected final ActorHandle<?> sender() {
        return this.sender;
    }
}
