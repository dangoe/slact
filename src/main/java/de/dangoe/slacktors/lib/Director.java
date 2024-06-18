package de.dangoe.slacktors.lib;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Director implements Context {

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

    <A extends AbstractActor<M>, M> ActorHandle<M> actorOf(final ActorPath path, final Supplier<A> factory) {
        final var actor = new ActorWrapper<>(path, factory.get(), this);
        actors.put(path, actor);
        return actor;
    }

    @Override
    public <A extends AbstractActor<M>, M> ActorHandle<M> actorOf(final String name, final Supplier<A> factory) {
        return actorOf(ActorPath.root().append(name), factory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends AbstractActor<M>, M> Optional<ActorHandle<M>> select(ActorPath path) {
        return Optional.ofNullable((ActorHandle<M>) this.actors.get(path));
    }

    public static Director forName(final String name) {
        return new Director(name);
    }


    boolean stopped() {
        return stopped.get();
    }

    Executor executor() {
        return executor;
    }
}
