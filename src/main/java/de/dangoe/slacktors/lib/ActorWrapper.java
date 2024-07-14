package de.dangoe.slacktors.lib;

import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

final class ActorWrapper<M> implements ActorHandle<M>, Context {

    private final class ActorScopedContext implements Context {

        @Override
        public <A extends AbstractActor<M>, M> ActorHandle<M> newActor(
            String name,
            Supplier<A> initializer
        ) {
            return ActorWrapper.this.newActor(name, initializer);
        }

        @Override
        public <A extends AbstractActor<M>, M> Optional<ActorHandle<M>> select(
            ActorPath path
        ) {
            return ActorWrapper.this.select(path);
        }
    }

    private final Queue<RoutedMessage<M>> messages =
        new LinkedBlockingQueue<>();

    private final ActorPath path;
    private final AbstractActor<M> delegate;
    private final Director director;

    public ActorWrapper(
        final ActorPath path,
        final AbstractActor<M> delegate,
        final Director director
    ) {
        super();
        this.path = path;
        this.delegate = delegate;
        this.director = director;

        director
            .executor()
            .execute(() -> {
                while (!director.stopped()) {
                    try {
                        final var msg = messages.poll();

                        if (msg != null) {
                            final var sender = select(msg.sender());
                            final var recipient = select(msg.recipient());

                            if (sender.isPresent() && recipient.isPresent()) {
                                final ActorHandle<?> senderHandle =
                                    sender.get();
                                final ActorHandle<M> recipientHandle =
                                    (ActorHandle<M>) recipient.get();
                                final M message = msg.message();
                                this.delegate.onMessage(
                                        message,
                                        senderHandle,
                                        recipientHandle,
                                        new ActorScopedContext()
                                    );
                            }
                        }

                        Thread.sleep(0, 50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
    }

    @Override
    public ActorPath path() {
        return this.path;
    }

    @Override
    public <A extends AbstractActor<M>, M> ActorHandle<M> newActor(
        final String name,
        final Supplier<A> factory
    ) {
        return director.actorOf(path().append(name), factory);
    }

    @Override
    public <A extends AbstractActor<M>, M> Optional<ActorHandle<M>> select(
        ActorPath path
    ) {
        if (path == ActorPath.root()) {
            return Optional.of((ActorHandle<M>) this.director);
        }

        return this.director.select(path);
    }

    @Override
    public void send(final M message, final ActorHandle<?> sender) {
        if (this.messages.size() < 10) {
            this.messages.add(
                    new RoutedMessage<>(sender.path(), this.path(), message)
                );
        } else {
            // TODO Use overflow strategy
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActorWrapper<?> that = (ActorWrapper<?>) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }
}
