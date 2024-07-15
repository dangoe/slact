package de.dangoe.concurrent.slact;

import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class ActorWrapper<M> implements ActorHandle<M> {

  private class ActorContextImpl implements ActorContext {

    private final String messageId;
    private final String correlationMessageId;
    private final ActorHandle<?> senderHandle;

    public ActorContextImpl(final String messageId, final String correlationMessageId,
        final ActorHandle<?> senderHandle) {
      super();
      this.messageId = messageId;
      this.correlationMessageId = correlationMessageId;
      this.senderHandle = senderHandle;
    }

    @Override
    public String messageId() {
      return this.messageId;
    }

    @Override
    public Optional<String> correlationMessageId() {
      return Optional.ofNullable(this.correlationMessageId);
    }

    @Override
    public ActorHandle<?> sender() {
      return this.senderHandle;
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
    public <A extends Actor<M1>, M1> ActorHandle<M1> register(
        final String name,
        ActorCreator<A> actorCreator) {
      return ((Slact) ActorWrapper.this.actorRegistry).newActor(self().path().append(name),
          actorCreator);
    }

    @Override
    public <M1> Optional<ActorHandle<M1>> resolve(final ActorPath path) {
      return ActorWrapper.this.actorHandleResolver.resolve(path);
    }

    @Override
    public <M1> SendableMessage<M1> send(M1 message) {
      return targetActor -> ((ActorWrapper<M1>) targetActor).sendInternal(message,
          messageId, self());
    }

    @Override
    public <M1> ForwardableMessage<M1> forward(M1 message) {
      return targetActor -> ((ActorWrapper<M1>) targetActor).forwardInternal(message,
          messageId, sender());
    }
  }

  private final Queue<WrappedMessage<M>> messages = new LinkedBlockingQueue<>();

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

        this.delegate.onMessage(message,
            new ActorContextImpl(msg.messageId(), msg.correlationMessageId().orElse(null),
                senderHandle));
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
  public <A extends Actor<M2>, M2> ActorHandle<M2> register(final String name,
      final ActorCreator<A> creator) {
    return ((Slact) ActorWrapper.this.actorRegistry).newActor(this.path.append(name), creator);
  }

  void sendInternal(final M message, final String correlationMessageId,
      final ActorHandle<?> sender) {
    processMessage(
        new WrappedMessage.FireAndForgetMessage<>(message, correlationMessageId, sender.path()),
        sender);
  }

  void forwardInternal(final M message, final String correlationMessageId, ActorHandle<?> sender) {
    processMessage(new WrappedMessage.AskMessage<>(message, correlationMessageId, sender.path()),
        sender);
  }

  private void processMessage(final WrappedMessage<M> message, final ActorHandle<?> sender) {
    if (this.messages.size() < 1000) {
      this.messages.add(message);
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
