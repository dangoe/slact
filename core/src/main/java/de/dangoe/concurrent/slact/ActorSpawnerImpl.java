package de.dangoe.concurrent.slact;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

class ActorSpawnerImpl implements ActorSpawner {

  private final Logger logger;

  private final ActorRegistry actorRegistry;
  private final ActorHandleResolver actorHandleResolver;
  private final ScheduledExecutorService executor;

  public ActorSpawnerImpl(final Logger logger, final ActorRegistry actorRegistry,
      final ActorHandleResolver actorHandleResolver, final ScheduledExecutorService executor) {
    super();
    this.logger = logger;
    this.actorRegistry = actorRegistry;
    this.actorHandleResolver = actorHandleResolver;
    this.executor = executor;
  }

  @Override
  public <A extends Actor<M>, M> ActorHandle<M> spawn(final String name,
      final ActorCreator<A> creator) {
    return spawnInternal(ActorPath.root().append(name), creator);
  }

  <A extends Actor<M>, M> ActorHandle<M> spawnInternal(final ActorPath path,
      final ActorCreator<A> creator) {

    final var actor = creator.create();

    final var localSpawner = new ActorSpawner() {

      @Override
      public <A1 extends Actor<M1>, M1> ActorHandle<M1> spawn(final String name,
          final ActorCreator<A1> actorCreator) {
        return spawnInternal(path.append(name), actorCreator);
      }
    };

    final var actorWrapper = new ActorWrapper<>(logger, actor, path, localSpawner,
        this.actorHandleResolver,
        new ScheduledExecutor() {

          @Override
          public void scheduleOnce(final Runnable command, final Duration initialDelay) {
            ActorSpawnerImpl.this.executor.schedule(command, initialDelay.toMillis(),
                TimeUnit.MILLISECONDS);
          }

          @Override
          public void scheduleAtFixedRate(final Runnable command, final Duration initialDelay,
              final Duration period) {
            ActorSpawnerImpl.this.executor.scheduleAtFixedRate(command, initialDelay.toMillis(),
                period.toMillis(), TimeUnit.MILLISECONDS);
          }
        });
    actorRegistry.add(actorWrapper);
    return actorWrapper;
  }
}
