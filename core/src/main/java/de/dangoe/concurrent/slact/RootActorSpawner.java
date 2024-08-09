package de.dangoe.concurrent.slact;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

class RootActorSpawner implements ActorSpawner {

  private final class ActorExterminatorImpl implements ActorExterminator {

    @Override
    public void exterminate(final @NotNull ActorPath path) {

      final var maybeActor = RootActorSpawner.this.actorHandleResolver.resolve(path);

      if (maybeActor.isEmpty()) {
        return;
      }

      final var actor = ((ActorWrapper<?>) maybeActor.get());

      actor.shutdown();

      RootActorSpawner.this.actorRegistry.unregister(actor);
    }
  }

  private final class PathLocalActorSpawner implements ActorSpawner {

    private final ActorPath path;

    private PathLocalActorSpawner(final @NotNull ActorPath path) {
      this.path = path;
    }

    @Override
    public @NotNull <A extends Actor<M>, M> ActorHandle<M> spawn(final @NotNull String name,
        final @NotNull ActorCreator<A> actorCreator) {
      return spawnInternal(path.append(name), actorCreator);
    }
  }

  private final Logger logger;

  private final ActorRegistry actorRegistry;
  private final ActorHandleResolver actorHandleResolver;
  private final ActorExterminatorImpl actorExterminator;
  private final ScheduledExecutor scheduledExecutor;

  public RootActorSpawner(final @NotNull Logger logger, final @NotNull ActorRegistry actorRegistry,
      final @NotNull ActorHandleResolver actorHandleResolver,
      final @NotNull ScheduledExecutor scheduledExecutor) {
    super();
    this.logger = logger;
    this.actorRegistry = actorRegistry;
    this.actorHandleResolver = actorHandleResolver;
    this.actorExterminator = new ActorExterminatorImpl();
    this.scheduledExecutor = scheduledExecutor;
  }

  @Override
  public @NotNull <A extends Actor<M>, M> ActorHandle<M> spawn(final @NotNull String name,
      final @NotNull ActorCreator<A> creator) {
    return spawnInternal(ActorPath.root().append(name), creator);
  }

  @NotNull
  <A extends Actor<M>, M> ActorHandle<M> spawnRootActor(final @NotNull ActorCreator<A> creator) {
    return spawnInternal(ActorPath.root(), creator);
  }

  private @NotNull <A extends Actor<M>, M> ActorHandle<M> spawnInternal(
      final @NotNull ActorPath path, final @NotNull ActorCreator<A> creator) {

    final var actor = creator.create();

    final var localSpawner = new PathLocalActorSpawner(path);

    final var actorWrapper = new ActorWrapper<>(logger, actor, path, localSpawner,
        this.actorExterminator, this.actorHandleResolver, this.scheduledExecutor);

    actorRegistry.register(actorWrapper);

    return actorWrapper;
  }
}
