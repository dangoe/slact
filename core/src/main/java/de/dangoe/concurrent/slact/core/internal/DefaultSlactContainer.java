package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorCreator;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.ActorRuntime;
import de.dangoe.concurrent.slact.core.Done;
import de.dangoe.concurrent.slact.core.FuturePipeOp;
import de.dangoe.concurrent.slact.core.ScheduledExecutor;
import de.dangoe.concurrent.slact.core.SlactContainer;
import de.dangoe.concurrent.slact.core.exception.ActorRegistrationException;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.StopActor;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultSlactContainer implements SlactContainer {

  private static class ActorRegistryImpl implements ActorRegistry {

    private final Map<ActorPath, ActorWrapper<?>> actors = new ConcurrentHashMap<>();

    @Override
    public void register(final @NotNull ActorWrapper<?> actor) {
      actors.merge(actor.path(), actor, (oldValue, newValue) -> {
        throw new ActorRegistrationException(actor.path());
      });
    }

    @Override
    public void unregister(final @NotNull ActorWrapper<?> actor) {
      actors.remove(actor.path());
    }

    @NotNull
    public Optional<ActorHandle<?>> get(final @NotNull ActorPath path) {
      return Optional.ofNullable(actors.get(path));
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(DefaultSlactContainer.class);

  private final ScheduledExecutor scheduledExecutor;

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final ActorRegistryImpl actorRegistry;
  private final RootActorSpawner rootActorSpawner;

  private final ActorHandle<Object> rootActor;

  // TODO Add configuration
  public DefaultSlactContainer(
      final @NotNull Supplier<ScheduledExecutor> scheduledExecutorFactory) {

    super();

    this.scheduledExecutor = scheduledExecutorFactory.get();

    Objects.requireNonNull(this.scheduledExecutor, "Scheduled executor must not be null!");

    this.actorRegistry = new ActorRegistryImpl();
    this.rootActorSpawner = new RootActorSpawner(logger, this.actorRegistry, this,
        this.scheduledExecutor);

    this.rootActor = this.rootActorSpawner.spawnRootActor(() -> new Actor<Object>() {
      @Override
      public void onMessage(@NotNull Object message) {
        logger.warn("Received message as root: '{}'.", message);
      }
    });
  }

  @Override
  public void close() throws Exception {
    shutdown();
  }

  @Override
  public void shutdown() throws Exception {
    this.stopped.compareAndExchange(false, true);
    try {
      // TODO Implement shutdown
    } finally {
      this.scheduledExecutor.close();
    }
  }

  @Override
  public @NotNull <A extends Actor<M>, M> ActorHandle<M> spawn(final @NotNull String name,
      final @NotNull ActorCreator<A, M> creator) {
    return this.rootActorSpawner.spawn(name, creator);
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull <M> Optional<ActorHandle<M>> resolve(final @NotNull ActorPath path) {

    if (path.isRoot()) {
      return Optional.of((ActorHandle<M>) this.rootActor);
    }

    return this.actorRegistry.get(path).map(actor -> (ActorWrapper<M>) actor);
  }

  @Override
  public boolean isStopped() {
    return this.stopped.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull <M> ActorRuntime.SendMessageOp<M> send(final @NotNull M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).sendInternal(message, this.rootActor);
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull <M> SendMessageOp<M> forward(final @NotNull M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).forwardInternal(message, this.rootActor);
  }

  @Override
  public @NotNull <M> ResponseRequestOp<M> requestResponseTo(final @NotNull M message) {

    return new ResponseRequestOp<>() {

      @Override
      @SuppressWarnings("unchecked")
      public @NotNull <R> ResponseRequestFromOp<M, R> ofType(final @NotNull Class<R> responseType) {

        return targetActor -> ((ActorWrapper<M>) targetActor).requestResponseToInternal(message,
            DefaultSlactContainer.this.rootActor);
      }
    };
  }

  @Override
  public @NotNull <M1> FuturePipeOp<M1> pipeFuture(final @NotNull Future<M1> eventualMessage) {
    return target -> this.scheduledExecutor.scheduleOnce(() -> {
      // TODO Configure timeout
      try {
        final var message = eventualMessage.get(10, TimeUnit.SECONDS);
        send(message).to(target);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        throw new RuntimeException(e);
      }
    }, Duration.of(0, ChronoUnit.NANOS));
  }

  @Override
  public @NotNull Future<Done> stop(final @NotNull ActorHandle<?> actor) {
    return ((ActorWrapper<?>) actor).requestResponseToLifecycleControlInternal(
        new StopActor(ActorPath.root()), this.rootActor).thenApply(it -> Done.instance());
  }
}
