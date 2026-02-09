package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorCreator;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorHandleResolver;
import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.ActorSpawner;
import de.dangoe.concurrent.slact.core.ScheduledExecutor;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.StartActor;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

final class RootActorSpawner implements ActorSpawner {

  private final class StopActorFn implements Consumer<ActorPath> {

    @Override
    public void accept(final @NotNull ActorPath path) {

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
        final @NotNull ActorCreator<A, M> actorCreator) {
      return spawnInternal(path.append(name), actorCreator);
    }
  }

  private final Logger logger;

  private final ActorRegistry actorRegistry;
  private final ActorHandleResolver actorHandleResolver;
  private final Consumer<ActorPath> stopActorFn;
  private final ScheduledExecutor scheduledExecutor;

  RootActorSpawner(final @NotNull Logger logger, final @NotNull ActorRegistry actorRegistry,
      final @NotNull ActorHandleResolver actorHandleResolver,
      final @NotNull ScheduledExecutor scheduledExecutor) {
    super();
    this.logger = logger;
    this.actorRegistry = actorRegistry;
    this.actorHandleResolver = actorHandleResolver;
    this.stopActorFn = new StopActorFn();
    this.scheduledExecutor = scheduledExecutor;
  }

  @Override
  public @NotNull <A extends Actor<M>, M> ActorHandle<M> spawn(final @NotNull String name,
      final @NotNull ActorCreator<A, M> creator) {
    return spawnInternal(ActorPath.root().append(name), creator);
  }

  @NotNull <A extends Actor<M>, M> ActorHandle<M> spawnRootActor(
      final @NotNull ActorCreator<A, M> creator) {
    return spawnInternal(ActorPath.root(), creator);
  }

  private @NotNull <A extends Actor<M>, M> ActorHandle<M> spawnInternal(
      final @NotNull ActorPath path, final @NotNull ActorCreator<A, M> creator) {

    final var actor = creator.create();

    final var localSpawner = new PathLocalActorSpawner(path);

    final var actorWrapper = new ActorWrapper<>(actor, path, localSpawner, this.stopActorFn,
        this.actorHandleResolver, this.scheduledExecutor);

    actorRegistry.register(actorWrapper);

    final var parentPath = path.isRoot() ? path
        : path.parent().orElseThrow(() -> new IllegalStateException("Root actor path is null."));

    actorWrapper.sendLifecycleControlMessage(new StartActor(parentPath));

    return actorWrapper;
  }
}
