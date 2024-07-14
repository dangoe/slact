package de.dangoe.slacktors.lib;

public abstract class AbstractActor<M> {

    private Context context;
    private ActorHandle<M> self;

    private ActorHandle<?> sender;

    final void onMessage(
        final M message,
        final ActorHandle<?> sender,
        final ActorHandle<M> self,
        final Context context
    ) {
        this.context = context;
        this.self = self;
        this.sender = sender;
        onMessage(message);
    }

    protected abstract void onMessage(M message);

    protected final Context context() {
        return this.context;
    }

    protected final ActorHandle<M> self() {
        return this.self;
    }

    protected final ActorHandle<?> sender() {
        return this.sender;
    }
}
