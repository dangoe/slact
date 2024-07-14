package de.dangoe.concurrent.slact;

import java.io.Serializable;

public abstract class AbstractActor<M extends Serializable> {

    private ActorContext context;

    private ActorHandle<?> sender;

    final void onMessage(final M message, final ActorHandle<?> sender, final ActorContext context) {
        this.context = context;
        this.sender = sender;
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
        return this.sender;
    }
}
