package de.dangoe.slacktors.lib;

import java.io.Serializable;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class ActorWrapper<M extends Serializable> implements ActorHandle<M> {

    private final Queue<RoutedMessage<M>> messages = new LinkedBlockingQueue<>();

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

            final var sender = this.context.select(msg.sender());
            final var recipient = this.context.select(msg.recipient());

            if (sender.isPresent() && recipient.isPresent()) {

                final ActorHandle<?> senderHandle = sender.get();
                @SuppressWarnings("unchecked") final ActorHandle<M> recipientHandle = (ActorHandle<M>) recipient.get();

                final M message = msg.message();

                this.delegate.onMessage(message, senderHandle, recipientHandle, this.context);
            }

            msg = messages.poll();
        }
    }

    @Override
    public ActorPath path() {
        return this.context.path();
    }

    @Override
    public <A extends AbstractActor<M2>, M2 extends Serializable> ActorHandle<M2> actorOf(final String name, final Supplier<A> initializer) {
        return this.context.actorOf(name, initializer);
    }

    @Override
    public void send(final M message, final ActorHandle<?> sender) {
        if (this.messages.size() < 10) {
            this.messages.add(new RoutedMessage<>(sender.path(), this.path(), message));
        } else {
            // TODO Use overflow strategy
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActorWrapper<?> that = (ActorWrapper<?>) o;
        return Objects.equals(this.context.path(), that.context.path());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.context.path());
    }
}
