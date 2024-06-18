package de.dangoe.slacktors.lib;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Director implements Context {

    private final class ActorWrapper<M> implements ActorHandle<M> {

        private final Queue<M> messages = new LinkedBlockingQueue<>();

        private final ActorPath path;
        private final Actor<M> delegate;

        public ActorWrapper(final ActorPath path, final Actor<M> delegate) {
            super();
            this.path = path;
            this.delegate = delegate;

            Director.this.executor.execute(() -> {
                while (!Director.this.stopped.get()) {
                    try {
                        final var msg = messages.poll();

                        if (msg != null) {
                            this.delegate.onMessage(msg, this, new Context() {

                                @Override
                                public <A extends Actor<M>, M> ActorHandle<M> actorOf(Class<A> type) {

                                    final ActorWrapper<M> actor;

                                    final var path = ActorWrapper.this.path().append(UUID.randomUUID().toString());

                                    try {
                                        actor = new ActorWrapper<>(path, type.getConstructor().newInstance());
                                    } catch (InstantiationException | IllegalAccessException |
                                             InvocationTargetException |
                                             NoSuchMethodException e) {
                                        throw new RuntimeException(e);
                                    }

                                    actors.put(path, actor);

                                    return actor;
                                }

                                @Override
                                public <A extends Actor<M>, M> ActorHandle<M> select(ActorPath path) {
                                    return (ActorHandle<M>) Director.this.actors.get(path);
                                }
                            });
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
        public void send(final M message) {
            if (this.messages.size() < 10) {
                this.messages.add(message);
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

    private final String name;

    private final Executor executor;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private final Map<ActorPath, ActorWrapper<?>> actors = new HashMap<>();

    private Director(final String name) {
        super();
        this.name = name;
        this.executor = Executors.newFixedThreadPool(12);
    }

    public void shutdown() {
        this.stopped.compareAndExchange(false, true);
    }

    @Override
    public <A extends Actor<M>, M> ActorHandle<M> actorOf(final Class<A> type) {

        final var path = ActorPath.root().append(UUID.randomUUID().toString());

        final ActorWrapper<M> actor;

        try {
            actor = new ActorWrapper<>(path, type.getConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        actors.put(path, actor);

        return actor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Actor<M>, M> ActorHandle<M> select(ActorPath path) {
        return (ActorHandle<M>) this.actors.get(path);
    }

    public static Director forName(final String name) {
        return new Director(name);
    }
}
