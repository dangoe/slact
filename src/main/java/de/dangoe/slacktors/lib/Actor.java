package de.dangoe.slacktors.lib;

public abstract class Actor<M> {

    private ActorHandle<M> self;
    private Context context;

    void onMessage(final M message, final ActorHandle<M> self, final Context context) {
        this.self = self;
        this.context = context;
        onMessage(message);
    }

    protected abstract void onMessage(M message);

    protected final ActorHandle<M> self() {
        return this.self;
    }

    protected final Context context() {
        return this.context;
    }
}
