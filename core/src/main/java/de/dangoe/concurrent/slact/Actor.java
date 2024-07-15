package de.dangoe.concurrent.slact;

import java.io.Serializable;

public abstract class Actor<M extends Serializable> {

    private ActorContext context;

    private ActorHandle<?> sender;

    final void onMessage(final M message, final ActorContext context) {
        this.context = context;
        onMessage(message);
    }

    protected abstract void onMessage(M message);

    protected final ActorContext context() {
        return this.context;
    }

    protected final ActorHandle<?> parent() {
        return this.context.parent();
    }

    protected final ActorHandle<?> self() {
        return this.context.self();
    }

    protected final ActorHandle<?> sender() {
        return this.context.sender();
    }
}
