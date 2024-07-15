package de.dangoe.concurrent.slact;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Slact implements ActorHandleResolver, ActorHandle<Serializable> {

    private class NestedActorContext<M extends Serializable> implements ActorContext {

        private final ActorPath parent;
        private final ActorPath self;
        private final ActorHandleResolver selector;

        public NestedActorContext(final ActorPath parent, final ActorPath self, final ActorHandleResolver selector) {
            super();
            this.parent = parent;
            this.self = self;
            this.selector = selector;
        }

        @Override
        public ActorPath parentPath() {
            return this.parent;
        }

        @Override
        public ActorPath selfPath() {
            return this.self;
        }

        @Override
        public <M1 extends Serializable> Optional<ActorHandle<M1>> resolve(final ActorPath path) {
            return this.selector.resolve(path);
        }

        @Override
        public <A1 extends Actor<M1>, M1 extends Serializable> ActorHandle<M1> register(final String name, final ActorCreator<A1> creator) {
            return newActor(this.self.append(name), creator);
        }
    }

    private final String name;

    private final ScheduledExecutorService executor;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private final Map<ActorPath, ActorWrapper<?>> actors = new HashMap<>();

    private Slact(final String name) {
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
    public <A extends Actor<M>, M extends Serializable> ActorHandle<M> register(final String name, final ActorCreator<A> creator) {
        return newActor(ActorPath.root().append(name), creator);
    }

    private <A extends Actor<M>, M extends Serializable> ActorHandle<M> newActor(final ActorPath path, final ActorCreator<A> creator) {
        final var actor = creator.create();
        final var actorWrapper = new ActorWrapper<>(actor, new NestedActorContext<>(path.parent().orElse(ActorPath.root()), path, this), executor);
        this.actors.put(path, actorWrapper);
        return actorWrapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Serializable> Optional<ActorHandle<M>> resolve(final ActorPath path) {

        if (path == ActorPath.root()) {
            return Optional.of((ActorHandle<M>) this);
        }

        return Optional.ofNullable((ActorHandle<M>) this.actors.get(path));
    }

    public static Slact createRuntime() {
        return createRuntime(UUID.randomUUID().toString());
    }

    public static Slact createRuntime(final String name) {
        return new Slact(name);
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
