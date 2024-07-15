package de.dangoe.concurrent.slact;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class ActorWrapper<M extends Serializable> implements ActorHandle<M> {

  private class ActorContextImpl implements ActorContext {

    private final ActorHandle<?> senderHandle;

    public ActorContextImpl(final ActorHandle<?> senderHandle) {
      super();
      this.senderHandle = senderHandle;
    }

    @Override
    public ActorHandle<?> sender() {
      return senderHandle;
    }

    @Override
    public ActorHandle<?> parent() {
      final var maybeParentPath = ActorWrapper.this.path.parent();

      if (maybeParentPath.isEmpty()) {
        throw new IllegalStateException(
            "Actor without a parent: %s".formatted(ActorWrapper.this.path));
      }

      return this.resolve(maybeParentPath.get()).orElseThrow(() -> new IllegalStateException(
          "Failed to resolve actor handle for '%s'.".formatted(maybeParentPath.get())));
    }

    @Override
    public ActorHandle<?> self() {
      final var selfPath = ActorWrapper.this.path;
      return this.resolve(selfPath).orElseThrow(() -> new IllegalStateException(
          "Failed to resolve actor handle for '%s'.".formatted(selfPath)));
    }

    @Override
    public <A extends Actor<M1>, M1 extends Serializable> ActorHandle<M1> register(
        final String name,
        ActorCreator<A> actorCreator) {
      return ActorWrapper.this.actorRegistry.register(name, actorCreator);
    }

    @Override
    public <M1 extends Serializable> Optional<ActorHandle<M1>> resolve(final ActorPath path) {
      return ActorWrapper.this.actorHandleResolver.resolve(path);
    }
  }

  private final Queue<TraceableMessage<M>> messages = new LinkedBlockingQueue<>();

  private final Actor<M> delegate;
  private final ActorPath path;
  private final ActorRegistry actorRegistry;
  private final ActorHandleResolver actorHandleResolver;

  public ActorWrapper(final Actor<M> delegate, final ActorPath path,
      final ActorRegistry actorRegistry, final ActorHandleResolver actorHandleResolver,
      final ScheduledExecutor scheduledExecutor) {

    super();

    this.delegate = delegate;
    this.path = path;
    this.actorRegistry = actorRegistry;
    this.actorHandleResolver = actorHandleResolver;

    scheduledExecutor.scheduleAtFixedRate(this::processMessages, 0, 150, TimeUnit.NANOSECONDS);
  }

  private void processMessages() {

    var msg = messages.poll();

    while (msg != null) {

      final var sender = this.actorHandleResolver.resolve(msg.sender());

      if (sender.isPresent()) {

        final ActorHandle<?> senderHandle = sender.get();

        final M message = msg.message();

        this.delegate.onMessage(message, new ActorContextImpl(senderHandle));
      } else {
        // TODO Error handling
      }

      msg = messages.poll();
    }
  }

  @Override
  public ActorPath path() {
    return this.path;
  }

  @Override
  public <A extends Actor<M2>, M2 extends Serializable> ActorHandle<M2> register(final String name,
      final ActorCreator<A> creator) {
    return this.actorRegistry.register(name, creator);
  }

  @Override
  public void send(final M message, final ActorHandle<?> sender) {
    processMessage(message, sender);
  }

  @Override
  public void forward(final M message, final ActorContext context) {
    processMessage(message, context.sender());
  }

  private void processMessage(final M message, final ActorHandle<?> sender) {
    if (this.messages.size() < 1000) {
      this.messages.add(new TraceableMessage<>(sender.path(), message));
    } else {
      // TODO Use overflow strategy
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ActorWrapper<?> that = (ActorWrapper<?>) o;
    return Objects.equals(this.path, that.path);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.path);
  }
}
