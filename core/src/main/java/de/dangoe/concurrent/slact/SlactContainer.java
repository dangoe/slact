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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlactContainer implements ActorHandleResolver, ActorSpawner {

  private final Logger logger;

  private final String name;

  private final ScheduledExecutorService executor;

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Map<ActorPath, ActorWrapper<?>> actors = new HashMap<>();

  private final ActorSpawnerImpl actorSpawner;
  private final ActorHandle<Object> rootActor;

  // TODO Add configuration
  private SlactContainer(final String name) {

    super();

    this.logger = LoggerFactory.getLogger(getClass());

    this.name = name;

    this.executor = Executors.newScheduledThreadPool(12);
    this.actorSpawner = new ActorSpawnerImpl(logger,
        actor -> SlactContainer.this.actors.put(actor.path(), actor), this, executor);

    this.rootActor = this.actorSpawner.spawnInternal(ActorPath.root(),
        () -> new Actor<Object>() {
          @Override
          public void onMessage(Object message) {
            logger.warn("Received message as root: '{}'.", message);
          }
        });
  }

  public void shutdown() {
    this.stopped.compareAndExchange(false, true);
    try {
      Thread.sleep(1000);
    } catch (final InterruptedException e) {
      logger.error("Shutdown has been interrupted.", e);
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

    if (path.isRoot()) {
      return Optional.of((ActorHandle<M>) this.rootActor);
    }

    return Optional.ofNullable((ActorHandle<M>) this.actors.get(path));
  }

  public String name() {
    return name;
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
