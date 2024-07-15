package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.api.Actor;
import de.dangoe.concurrent.slact.api.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.api.ActorContext.PreparedSendMessageWithResponseRequestOp;
import de.dangoe.concurrent.slact.api.ActorCreator;
import de.dangoe.concurrent.slact.api.ActorHandle;
import de.dangoe.concurrent.slact.api.ActorHandleResolver;
import de.dangoe.concurrent.slact.api.ActorPath;
import de.dangoe.concurrent.slact.api.ActorSpawner;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlactContainer implements ActorHandleResolver, ActorSpawner {

  class ActorSpawnerImpl implements ActorSpawner {

    @Override
    public <A extends Actor<M>, M> ActorHandle<M> spawn(final String name,
        final ActorCreator<A> creator) {
      return spawnInternal(ActorPath.root().append(name), creator);
    }

    <A extends Actor<M>, M> ActorHandle<M> spawnInternal(final ActorPath path,
        final ActorCreator<A> creator) {
      final var actor = creator.create();
      final var actorWrapper = new ActorWrapper<>(actor, path, this, SlactContainer.this,
          new ScheduledExecutor() {

            @Override
            public ScheduledFuture<?> scheduleOnce(final Runnable command,
                final Duration initialDelay) {
              return SlactContainer.this.executor.schedule(command, initialDelay.toMillis(),
                  TimeUnit.MILLISECONDS);
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command,
                final Duration initialDelay, final Duration period) {
              return SlactContainer.this.executor.scheduleAtFixedRate(command, initialDelay.toMillis(),
                  period.toMillis(), TimeUnit.MILLISECONDS);
            }
          });
      SlactContainer.this.actors.put(path, actorWrapper);
      return actorWrapper;
    }
  }

  private final String name;

  private final ScheduledExecutorService executor;

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Map<ActorPath, ActorWrapper<?>> actors = new HashMap<>();

  private final ActorSpawnerImpl actorSpawner;
  private final ActorHandle<Object> rootActor;

  private SlactContainer(final String name) {

    super();

    this.name = name;

    this.actorSpawner = new ActorSpawnerImpl();
    this.executor = Executors.newScheduledThreadPool(12);

    this.rootActor = this.actorSpawner.spawnInternal(ActorPath.root(), () -> new Actor<Object>() {
      @Override
      protected void onMessageInternal(Object message) {
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

  public <M> PreparedSendMessageOp<M> send(final M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).sendInternal(message, null,
        this.rootActor);
  }

  public <M, R> PreparedSendMessageWithResponseRequestOp<M, R> requestResponseTo(final M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).requestResponseToInternal(message,
        this.rootActor);
  }
}
