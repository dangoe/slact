package de.dangoe.concurrent.slact;

import java.io.Serializable;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class ActorWrapper<M extends Serializable> implements ActorHandle<M> {

    private final Queue<TrackedMessage<M>> messages = new LinkedBlockingQueue<>();

    private final AbstractActor<M> delegate;
    private final ActorContext context;

    public ActorWrapper(final AbstractActor<M> delegate, final ActorContext context, final ScheduledExecutorService executorService) {

        super();

        this.delegate = delegate;
        this.context = context;

        executorService.scheduleAtFixedRate(this::processMessages, 0, 150, TimeUnit.NANOSECONDS);
    }

    private void processMessages() {

        var msg = messages.poll();

        while (msg != null) {

            final var sender = this.context.resolve(msg.sender());

            if (sender.isPresent()) {

                final ActorHandle<?> senderHandle = sender.get();

                final M message = msg.message();

                this.delegate.onMessage(message, senderHandle, this.context);
            } else {
                // TODO Error handling
            }

            msg = messages.poll();
        }
    }

    @Override
    public ActorPath path() {
        return this.context.selfPath();
    }

    @Override
    public <A extends AbstractActor<M2>, M2 extends Serializable> ActorHandle<M2> register(final String name, final ActorCreator<A> creator) {
        return this.context.register(name, creator);
    }

    @Override
    public void send(final M message, final ActorHandle<?> sender) {
        if (this.messages.size() < 10) {
            this.messages.add(new TrackedMessage<>(sender.path(), message));
        } else {
            // TODO Use overflow strategy
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActorWrapper<?> that = (ActorWrapper<?>) o;
        return Objects.equals(this.path(), that.path());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.path());
    }
}
