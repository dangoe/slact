package de.dangoe.slacktors.lib;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Director implements ActorContext, ActorHandle<Serializable> {

    private class NestedActorContext implements ActorContext {

        private final ActorPath path;
        private final ActorSelector actorSelector;

        public NestedActorContext(final ActorPath path, final ActorSelector actorSelector) {
            super();
            this.path = path;
            this.actorSelector = actorSelector;
        }

        @Override
        public ActorPath path() {
            return this.path;
        }

        @Override
        public <M extends Serializable> Optional<ActorHandle<M>> select(final ActorPath path) {
            return this.actorSelector.select(path);
        }

        @Override
        public <A extends AbstractActor<M>, M extends Serializable> ActorHandle<M> actorOf(final String name, final Supplier<A> initializer) {
            return newActor(path().append(name), initializer);
        }
    }

    private final String name;

    private final ScheduledExecutorService executor;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private final Map<ActorPath, ActorWrapper<?>> actors = new HashMap<>();

    private Director(final String name) {
        super();
        this.name = name;
        this.executor = Executors.newScheduledThreadPool(12);
    }

    public void shutdown() {
        this.stopped.compareAndExchange(false, true);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        executor.close();
    }

    @Override
    public <A extends AbstractActor<M>, M extends Serializable> ActorHandle<M> actorOf(final String name, final Supplier<A> initializer) {
        return newActor(ActorPath.root().append(name), initializer);
    }

    private <A extends AbstractActor<M>, M extends Serializable> ActorHandle<M> newActor(final ActorPath path, final Supplier<A> initializer) {
        final var actor = new ActorWrapper<>(initializer.get(), new NestedActorContext(path, this), executor);
        this.actors.put(path, actor);
        return actor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Serializable> Optional<ActorHandle<M>> select(final ActorPath path) {

        if (path == ActorPath.root()) {
            return Optional.of((ActorHandle<M>) this);
        }

        return Optional.ofNullable((ActorHandle<M>) this.actors.get(path));
    }

    public static Director forName(final String name) {
        return new Director(name);
    }

    boolean stopped() {
        return stopped.get();
    }

    @Override
    public ActorPath path() {
        return ActorPath.root();
    }

    @Override
    public void send(Serializable message, ActorHandle<?> sender) {
        System.out.println(message);
    }
}
