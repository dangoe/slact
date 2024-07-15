package de.dangoe.concurrent.slact;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Slact implements ActorHandleResolver, ActorHandle<Serializable> {

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
  public <A extends Actor<M>, M extends Serializable> ActorHandle<M> register(final String name,
      final ActorCreator<A> creator) {
    return newActor(ActorPath.root().append(name), creator);
  }

  private <A extends Actor<M>, M extends Serializable> ActorHandle<M> newActor(final ActorPath path,
      final ActorCreator<A> creator) {
    final var actor = creator.create();
    final var actorWrapper = new ActorWrapper<>(actor, path,
        this, this, Slact.this.executor::scheduleAtFixedRate);
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

  @Override
  public void forward(Serializable message, ActorContext context) {
    System.out.println(message);
  }
}
