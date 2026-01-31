package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorContext.CompletableSendMessageWithResponseRequestOp;
import de.dangoe.concurrent.slact.ActorContext.IntermediateSendMessageWithResponseRequestOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.MailboxItem.StopMessage;
import de.dangoe.concurrent.slact.exception.ActorRegistrationException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultSlactContainer implements SlactContainer {

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

  private static final Logger logger = LoggerFactory.getLogger(SlactContainer.class);

  private final ScheduledExecutor scheduledExecutor;

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final ActorRegistryImpl actorRegistry;
  private final RootActorSpawner rootActorSpawner;

  private final ActorHandle<Object> rootActor;

  // TODO Add configuration
  DefaultSlactContainer(final @NotNull Supplier<ScheduledExecutor> scheduledExecutorFactory) {

    super();

    this.scheduledExecutor = scheduledExecutorFactory.get();

    Objects.requireNonNull(this.scheduledExecutor, "Scheduled executor must not be null!");

    this.actorRegistry = new ActorRegistryImpl();
    this.rootActorSpawner = new RootActorSpawner(logger, actorRegistry, this,
        this.scheduledExecutor);

    this.rootActor = this.rootActorSpawner.spawnRootActor(() -> new Actor<Object>() {
      @Override
      public void onMessage(@NotNull Object message) {
        logger.warn("Received message as root: '{}'.", message);
      }
    });
  }

  @Override
  public void shutdown() throws Exception {
    this.stopped.compareAndExchange(false, true);
    try {
      Thread.sleep(1000);
    } catch (final InterruptedException e) {
      logger.error("Shutdown has been interrupted.", e);
    } finally {
      scheduledExecutor.close();
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
    return stopped.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull <M> PreparedSendMessageOp<M> send(final @NotNull M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).sendInternal(message, this.rootActor);
  }

  @Override
  public @NotNull <M> IntermediateSendMessageWithResponseRequestOp<M> requestResponseTo(
      @NotNull M message) {

    return new IntermediateSendMessageWithResponseRequestOp<>() {

      @Override
      @SuppressWarnings("unchecked")
      public @NotNull <R> CompletableSendMessageWithResponseRequestOp<M, R> ofType(
          @NotNull Class<R> responseType) {

        return targetActor -> ((ActorWrapper<M>) targetActor).requestResponseToInternal(message,
            DefaultSlactContainer.this.rootActor);
      }
    };
  }

  @Override
  public void stop(final @NotNull ActorHandle<?> actor) {
    ((ActorWrapper<?>) actor).sendLifecycleControlMessage(new StopMessage(this.rootActor.path()));
  }
}
