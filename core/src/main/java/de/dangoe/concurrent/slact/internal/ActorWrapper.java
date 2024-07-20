package de.dangoe.concurrent.slact.internal;

import de.dangoe.concurrent.slact.SlactContainer.ActorSpawnerImpl;
import de.dangoe.concurrent.slact.internal.WrappedMessage.FireAndForgetMessage;
import de.dangoe.concurrent.slact.internal.WrappedMessage.MessageWithResponseRequest;
import de.dangoe.concurrent.slact.Actor;
import de.dangoe.concurrent.slact.ActorContext;
import de.dangoe.concurrent.slact.ActorCreator;
import de.dangoe.concurrent.slact.ActorHandle;
import de.dangoe.concurrent.slact.ActorHandleResolver;
import de.dangoe.concurrent.slact.ActorPath;
import de.dangoe.concurrent.slact.EventualPipeOp;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ActorWrapper<M> implements ActorHandle<M> {

  private class ActorContextImpl implements ActorContext {

    private final WrappedMessage<M> message;
    private final ActorHandle<?> senderHandle;

    public ActorContextImpl(final WrappedMessage<M> message, final ActorHandle<?> senderHandle) {
      super();
      this.message = message;
      this.senderHandle = senderHandle;
    }

    @Override
    public String messageId() {
      return this.message.messageId();
    }

    @Override
    public Optional<String> correlationMessageId() {
      return this.message.correlationMessageId();
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
    public <M1> EventualPipeOp<M1> pipeEventually(final Future<M1> eventualMessage) {
      return target -> {
        ActorWrapper.this.scheduledExecutor.scheduleOnce(() -> {
          // TODO Configure timeout
          try {
            final var message = eventualMessage.get(10, TimeUnit.SECONDS);
            send(message).to(target);
          } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
          }
        }, Duration.of(0, ChronoUnit.NANOS));
      };
    }

    @Override
    public <A extends Actor<M1>, M1> ActorHandle<M1> spawn(final String name,
        ActorCreator<A> actorCreator) {
      return ActorWrapper.this.actorSpawner.spawnInternal(self().path().append(name), actorCreator);
    }

    @Override
    public <M1> Optional<ActorHandle<M1>> resolve(final ActorPath path) {
      return ActorWrapper.this.actorHandleResolver.resolve(path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M1> PreparedSendMessageOp<M1> send(final M1 message) {
      return targetActor -> {
        if (this.message instanceof WrappedMessage.MessageWithResponseRequest
            && sender().path() == ActorPath.root()) {
          completeResponseRequest(message);
        } else {
          ((ActorWrapper<M1>) targetActor).sendInternal(message,
              this.message.messageId(), self());
        }
      };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M1> void reply(final M1 message) {
      send(message).to((ActorWrapper<M1>) sender());
    }

    private <M1> void completeResponseRequest(final M1 message) {
      ((WrappedMessage.MessageWithResponseRequest<?, M1>) this.message).futureInternal()
          .complete(message);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M1> PreparedForwardMessageOp<M1> forward(M1 message) {
      return targetActor -> ((ActorWrapper<M1>) targetActor).forwardInternal(message,
          this.message.messageId(), sender());
    }
  }

  private final Queue<WrappedMessage<M>> messages = new LinkedBlockingQueue<>();

  private final Actor<M> delegate;
  private final ActorPath path;
  private final ActorSpawnerImpl actorSpawner;
  private final ActorHandleResolver actorHandleResolver;
  private final ScheduledExecutor scheduledExecutor;

  public ActorWrapper(final Actor<M> delegate, final ActorPath path,
      final ActorSpawnerImpl actorSpawner, final ActorHandleResolver actorHandleResolver,
      final ScheduledExecutor scheduledExecutor) {

    super();

    this.delegate = delegate;
    this.path = path;
    this.actorSpawner = actorSpawner;
    this.actorHandleResolver = actorHandleResolver;
    this.scheduledExecutor = scheduledExecutor;

    scheduledExecutor.scheduleAtFixedRate(this::processMessages, Duration.of(0, ChronoUnit.MILLIS),
        Duration.of(1, ChronoUnit.MILLIS));
  }

  private void processMessages() {

    var msg = messages.poll();

    while (msg != null) {

      final var sender = this.actorHandleResolver.resolve(msg.sender());

      if (sender.isPresent()) {

        final ActorHandle<?> senderHandle = sender.get();

        final M message = msg.message();

        this.delegate.onMessage(message, new ActorContextImpl(msg, senderHandle));
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
  public <A extends Actor<M2>, M2> ActorHandle<M2> spawn(final String name,
      final ActorCreator<A> creator) {
    return this.actorSpawner.spawnInternal(this.path.append(name), creator);
  }

  public void sendInternal(final M message, final String correlationMessageId,
      final ActorHandle<?> sender) {
    appendMessage(
        new WrappedMessage.FireAndForgetMessage<>(message, correlationMessageId, sender.path()),
        sender);
  }

  public <R> Future<R> requestResponseToInternal(final M message, final ActorHandle<?> sender) {

    final var wrapper = new MessageWithResponseRequest<M, R>(message, null, sender.path());

    appendMessage(wrapper, sender);

    return wrapper.future();
  }

  void forwardInternal(final M message, final String correlationMessageId, ActorHandle<?> sender) {
    appendMessage(new FireAndForgetMessage<>(message, correlationMessageId, sender.path()), sender);
  }

  private void appendMessage(final WrappedMessage<M> message, final ActorHandle<?> sender) {
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
