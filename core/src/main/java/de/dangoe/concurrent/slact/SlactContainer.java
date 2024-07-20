package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageWithResponseRequestOp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlactContainer implements ActorHandleResolver, ActorSpawner {

  private final String name;

  private final ScheduledExecutorService executor;

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Map<ActorPath, ActorWrapper<?>> actors = new HashMap<>();

  private final ActorSpawnerImpl actorSpawner;
  private final ActorHandle<Object> rootActor;

  private SlactContainer(final String name) {

    super();

    this.name = name;

    this.executor = Executors.newScheduledThreadPool(12);
    this.actorSpawner = new ActorSpawnerImpl(new ActorRegistry() {
      @Override
      public void add(ActorWrapper<?> actor) {
        SlactContainer.this.actors.put(actor.path(), actor);
      }
    }, this, executor);

    this.rootActor = this.actorSpawner.spawnInternal(ActorPath.root(), () -> new Actor<Object>() {
      @Override
      public void onMessage(Object message) {
        System.out.println(message);
      }
    });
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
  public <A extends Actor<M>, M> ActorHandle<M> spawn(final String name,
      final ActorCreator<A> creator) {
    return this.actorSpawner.spawn(name, creator);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <M> Optional<ActorHandle<M>> resolve(final ActorPath path) {

    if (path == ActorPath.root()) {
      return Optional.of((ActorHandle<M>) this.rootActor);
    }

    return Optional.ofNullable((ActorHandle<M>) this.actors.get(path));
  }

  public static SlactContainer create() {
    return create(UUID.randomUUID().toString());
  }

  public static SlactContainer create(final String name) {
    return new SlactContainer(name);
  }

  boolean stopped() {
    return stopped.get();
  }

  @SuppressWarnings("unchecked")
  public <M> PreparedSendMessageOp<M> send(final M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).sendInternal(message, null,
        this.rootActor);
  }

  @SuppressWarnings("unchecked")
  public <M, R> PreparedSendMessageWithResponseRequestOp<M, R> requestResponseTo(final M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).requestResponseToInternal(message,
        this.rootActor);
  }
}
