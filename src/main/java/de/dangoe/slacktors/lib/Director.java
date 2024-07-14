package de.dangoe.slacktors.lib;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Director implements Context, ActorHandle<Object> {

    private final String name;

    private final ExecutorService executor;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private final Map<ActorPath, ActorWrapper<?>> actors = new HashMap<>();

    private Director(final String name) {
        super();
        this.name = name;
        this.executor = Executors.newFixedThreadPool(12);
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

    <A extends AbstractActor<M>, M> ActorHandle<M> actorOf(
        final ActorPath path,
        final Supplier<A> factory
    ) {
        final var actor = new ActorWrapper<>(path, factory.get(), this);
        actors.put(path, actor);
        return actor;
    }

    @Override
    public <A extends AbstractActor<M>, M> ActorHandle<M> newActor(
        final String name,
        final Supplier<A> factory
    ) {
        return actorOf(ActorPath.root().append(name), factory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends AbstractActor<M>, M> Optional<ActorHandle<M>> select(
        ActorPath path
    ) {
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

    @Override
    public ActorPath path() {
        return ActorPath.root();
    }

    @Override
    public void send(Object message, ActorHandle<?> sender) {
        System.out.println(message);
    }
}
