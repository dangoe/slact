package de.dangoe.concurrent.slact;

import org.slf4j.Logger;

class RootActorSpawner implements ActorSpawner {

  private final Logger logger;

  private final ActorRegistry actorRegistry;
  private final ActorHandleResolver actorHandleResolver;
  private final ScheduledExecutor scheduledExecutor;

  public RootActorSpawner(final Logger logger, final ActorRegistry actorRegistry,
      final ActorHandleResolver actorHandleResolver, final ScheduledExecutor scheduledExecutor) {
    super();
    this.logger = logger;
    this.actorRegistry = actorRegistry;
    this.actorHandleResolver = actorHandleResolver;
    this.scheduledExecutor = scheduledExecutor;
  }

  @Override
  public <A extends Actor<M>, M> ActorHandle<M> spawn(final String name,
      final ActorCreator<A> creator) {
    return spawnInternal(ActorPath.root().append(name), creator);
  }

  <A extends Actor<M>, M> ActorHandle<M> spawnRootActor(final ActorCreator<A> creator) {
    return spawnInternal(ActorPath.root(), creator);
  }

  private <A extends Actor<M>, M> ActorHandle<M> spawnInternal(final ActorPath path,
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
        new ActorExterminator() {
          @Override
          public void exterminate(final ActorPath path) {

            final var maybeActor = actorHandleResolver.resolve(path);

            if (maybeActor.isEmpty()) {
              return;
            }

            final var actor = ((ActorWrapper<?>) maybeActor.get());

            actor.shutdown();

            actorRegistry.unregister(actor);
          }
        },
        this.actorHandleResolver, this.scheduledExecutor);
    actorRegistry.register(actorWrapper);
    return actorWrapper;
  }
}
