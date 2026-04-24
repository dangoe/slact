// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorCreator;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorHandleResolver;
import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.ActorSpawner;
import de.dangoe.concurrent.slact.core.ScheduledExecutor;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.CompleteStartActorCommand;
import de.dangoe.concurrent.slact.core.logging.internal.Slf4jActorLogger;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

final class RootActorSpawner implements ActorSpawner {

  private final class UnregisterActorFn implements Consumer<ActorPath> {

    @Override
    public void accept(final @NotNull ActorPath path) {
      RootActorSpawner.this.actorRegistry.unregister(path);
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


  private final @NotNull UUID containerId;
  private final @NotNull ActorRegistry actorRegistry;
  private final @NotNull ActorHandleResolver actorHandleResolver;
  private final @NotNull Consumer<ActorPath> unregisterActorFn;
  private final @NotNull ScheduledExecutor scheduledExecutor;

  RootActorSpawner(final @NotNull UUID containerId, final @NotNull ActorRegistry actorRegistry,
      final @NotNull ActorHandleResolver actorHandleResolver,
      final @NotNull ScheduledExecutor scheduledExecutor) {
    super();
    this.containerId = containerId;
    this.actorRegistry = actorRegistry;
    this.actorHandleResolver = actorHandleResolver;
    this.unregisterActorFn = new UnregisterActorFn();
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

    //noinspection unchecked
    final var logger = new Slf4jActorLogger(this.containerId, path,
        (Class<Actor<?>>) actor.getClass());

    final var localSpawner = new PathLocalActorSpawner(path);

    final var actorWrapper = new ActorWrapper<>(logger, actor, path, localSpawner,
        this.unregisterActorFn, this.actorHandleResolver, this.scheduledExecutor);

    actorRegistry.register(actorWrapper);

    if (!path.isRoot()) {

      final var parentActor = path.parent().flatMap(this.actorRegistry::get).orElseThrow(
          () -> new IllegalStateException(
              "Failed to resolve parent actor for '%s'.".formatted(path)));

      parentActor.registerChild(actorWrapper);
      actorWrapper.sendLifecycleControlMessage(new CompleteStartActorCommand(parentActor.path()));
    }

    return actorWrapper;
  }
}
