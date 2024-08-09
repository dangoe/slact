package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageWithResponseRequestOp;
import de.dangoe.concurrent.slact.exception.ActorRegistrationException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultSlactContainer implements SlactContainer {

  private static class ActorRegistryImpl implements ActorRegistry {

    private final Map<ActorPath, ActorWrapper<?>> actors = new ConcurrentHashMap<>();

    @Override
    public void add(final ActorWrapper<?> actor) {
      actors.merge(actor.path(), actor, (oldValue, newValue) -> {
        throw new ActorRegistrationException(actor.path());
      });
    }

    public Optional<ActorHandle<?>> get(ActorPath path) {
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
  DefaultSlactContainer(final String name,
      final Supplier<ScheduledExecutor> scheduledExecutorFactory) {

    super();

    this.scheduledExecutor = scheduledExecutorFactory.get();

    Objects.requireNonNull(this.scheduledExecutor, "Scheduled executor must not be null!");

    this.actorRegistry = new ActorRegistryImpl();
    this.rootActorSpawner = new RootActorSpawner(logger, actorRegistry, this,
        this.scheduledExecutor);

    this.rootActor = this.rootActorSpawner.spawnRootActor(() -> new Actor<Object>() {
      @Override
      public void onMessage(Object message) {
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
  public <A extends Actor<M>, M> ActorHandle<M> spawn(final String name,
      final ActorCreator<A> creator) {
    return this.rootActorSpawner.spawn(name, creator);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <M> Optional<ActorHandle<M>> resolve(final ActorPath path) {

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
  public <M> PreparedSendMessageOp<M> send(final M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).sendInternal(message, null,
        this.rootActor);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <M, R> PreparedSendMessageWithResponseRequestOp<M, R> requestResponseTo(final M message) {
    return targetActor -> ((ActorWrapper<M>) targetActor).requestResponseToInternal(message,
        this.rootActor);
  }
}
