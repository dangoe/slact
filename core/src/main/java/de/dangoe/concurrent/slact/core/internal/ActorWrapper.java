package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorContext;
import de.dangoe.concurrent.slact.core.ActorCreator;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorHandleResolver;
import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.ActorSpawner;
import de.dangoe.concurrent.slact.core.Cancellable;
import de.dangoe.concurrent.slact.core.Done;
import de.dangoe.concurrent.slact.core.FuturePipeOp;
import de.dangoe.concurrent.slact.core.ScheduledExecutor;
import de.dangoe.concurrent.slact.core.exception.MessageRejectedException;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.FireAndForgetMessage;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.LifecycleControlMessage;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.MessageWithResponseRequest;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.StopMessage;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.StoppedMessage;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.WrappedMessage;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

final class ActorWrapper<M> implements ActorHandle<M> {

  private class ActorContextImpl implements ActorContext<M> {

    private final ActorHandle<?> senderHandle;

    public ActorContextImpl(@NotNull final ActorHandle<?> senderHandle) {
      super();
      this.senderHandle = senderHandle;
    }

    @Override
    public @NotNull ActorHandle<?> sender() {
      return this.senderHandle;
    }

    @Override
    public @NotNull ActorHandle<?> parent() {
      final var maybeParentPath = ActorWrapper.this.path.parent();

      if (maybeParentPath.isEmpty()) {
        throw new IllegalStateException(
            "Actor without a parent: %s".formatted(ActorWrapper.this.path));
      }

      return this.resolve(maybeParentPath.get()).orElseThrow(() -> new IllegalStateException(
          "Failed to resolve actor handle for '%s'.".formatted(maybeParentPath.get())));
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull ActorHandle<M> self() {
      final var selfPath = ActorWrapper.this.path;
      return (ActorHandle<M>) this.resolve(selfPath).orElseThrow(() -> new IllegalStateException(
          "Failed to resolve actor handle for '%s'.".formatted(selfPath)));
    }

    @Override
    public @NotNull <M1> FuturePipeOp<M1> pipeFuture(final @NotNull Future<M1> eventualMessage) {
      return target -> ActorWrapper.this.scheduledExecutor.scheduleOnce(() -> {
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
    public @NotNull <A extends Actor<M1>, M1> ActorHandle<M1> spawn(final @NotNull String name,
        @NotNull ActorCreator<A, M1> actorCreator) {
      return ActorWrapper.this.spawn(name, actorCreator);
    }

    @Override
    public @NotNull <M1> Optional<ActorHandle<M1>> resolve(final @NotNull ActorPath path) {
      return ActorWrapper.this.actorHandleResolver.resolve(path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <M1> SendMessageOp<M1> send(final @NotNull M1 message) {
      return targetActor -> {

        final var messageWithResponseRequest = ActorWrapper.this.messagesWithResponseRequest.get(
            targetActor.path());

        if (messageWithResponseRequest != null) {
          ActorWrapper.this.messagesWithResponseRequest.remove(targetActor.path());
          completeResponseRequest((MessageWithResponseRequest<?, M1>) messageWithResponseRequest,
              message);
        } else {
          ((ActorWrapper<M1>) targetActor).sendInternal(message, self());
        }
      };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M1> void respondWith(@NotNull M1 message) {
      send(message).to((ActorWrapper<M1>) sender());
    }

    @Override
    public @NotNull <M1> ResponseRequestOp<M1> requestResponseTo(@NotNull M1 message) {

      return new ResponseRequestOp<>() {

        @Override
        @SuppressWarnings("unchecked")
        public @NotNull <R> ResponseRequestFromOp<M1, R> ofType(
            final @NotNull Class<R> responseType) {

          return targetActor -> ((ActorWrapper<M1>) targetActor).requestResponseToInternal(message,
              sender());
        }
      };
    }

    private <M1> void completeResponseRequest(
        final MessageWithResponseRequest<?, M1> originalMessage, final M1 responseMessage) {
      originalMessage.futureInternal().complete(responseMessage);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <M1> SendMessageOp<M1> forward(final @NotNull M1 message) {
      return targetActor -> ((ActorWrapper<M1>) targetActor).forwardInternal(message, sender());
    }

    @Override
    public @NotNull Future<Done> stop(final @NotNull ActorHandle<?> actor) {
      return ((ActorWrapper<?>) actor).requestResponseToLifecycleControlInternal(
          new StopMessage(sender().path()), sender()).thenApply(it -> Done.instance());
    }
  }

  private final @NotNull Logger logger;
  private final @NotNull Actor<M> delegate;
  private final @NotNull ActorPath path;
  private final @NotNull ActorSpawner actorSpawner;
  private final @NotNull Consumer<ActorPath> stopActorFn;
  private final @NotNull ActorHandleResolver actorHandleResolver;
  private final @NotNull ScheduledExecutor scheduledExecutor;

  private final @NotNull Cancellable messagePoller;
  private final @NotNull ActiveActorContextHolder activeActorContextHolder;

  private final @NotNull Queue<MailboxItem> mailboxItems = new LinkedBlockingQueue<>();
  private final @NotNull Map<ActorPath, MessageWithResponseRequest<M, ?>> messagesWithResponseRequest;
  private final @NotNull List<ActorWrapper<?>> children = new CopyOnWriteArrayList<>();

  private final @NotNull AtomicReference<ActorState> state = new AtomicReference<>(
      ActorState.CONSTRUCTED);

  public ActorWrapper(final @NotNull Logger logger, final @NotNull Actor<M> delegate,
      final @NotNull ActorPath path, final @NotNull ActorSpawner actorSpawner,
      final @NotNull Consumer<ActorPath> stopActorFn,
      final @NotNull ActorHandleResolver actorHandleResolver,
      final @NotNull ScheduledExecutor scheduledExecutor) {

    super();

    this.logger = logger;
    this.delegate = delegate;
    this.path = path;
    this.actorSpawner = actorSpawner;
    this.stopActorFn = stopActorFn;
    this.actorHandleResolver = actorHandleResolver;
    this.scheduledExecutor = scheduledExecutor;

    this.messagePoller = scheduledExecutor.scheduleAtFixedRate(this::processMessages,
        Duration.of(0, ChronoUnit.MILLIS), Duration.of(1, ChronoUnit.MILLIS));
    this.activeActorContextHolder = ActiveActorContextHolder.getInstance();

    this.messagesWithResponseRequest = new HashMap<>();
  }

  @SuppressWarnings("unchecked")
  private void processMessages() {

    var item = mailboxItems.poll();

    while (item != null) {

      final var sender = this.actorHandleResolver.resolve(item.sender());

      if (sender.isEmpty()) {
        this.logger.warn("Failed to resolve sender for message with ID '{}'.", item.id());
        break;
      }

      final var senderHandle = sender.get();
      final var actorContext = new ActorContextImpl(senderHandle);

      this.activeActorContextHolder.activateContext(actorContext);

      try {
        if (item instanceof MailboxItem.StartMessage) {
          if (!state.compareAndSet(ActorState.CONSTRUCTED, ActorState.STARTED)) {
            return;
          }
          this.delegate.onStart();
          this.state.compareAndSet(ActorState.STARTED, ActorState.READY);
        } else if (item instanceof WrappedMessage<?>) {
          final var message = ((WrappedMessage<M>) item);

          try {
            if (message instanceof MessageWithResponseRequest<M, ?> messageWithResponseRequest) {
              this.messagesWithResponseRequest.put(senderHandle.path(), messageWithResponseRequest);
            }

            final var wrappedMessage = message.message();

            if (wrappedMessage instanceof StopMessage) {
              this.state.set(ActorState.STOPPING);
              this.children.forEach(
                  child -> child.sendLifecycleControlMessage(
                      new StopMessage(actorContext.self().path())));
              this.delegate.onStop();
              this.stopActorFn.accept(path());
              ((ActorWrapper<?>) actorContext.parent()).sendLifecycleControlMessage(
                  new StoppedMessage(path()));
              actorContext.respondWith(new StoppedMessage(path()));
              this.state.set(ActorState.STOPPED);
            } else {
              this.delegate.receiveMessage(wrappedMessage);
            }
          } catch (final MessageRejectedException e) {
            this.logger.warn("Message has been rejected.", e);
          }
        }
      } finally {
        this.activeActorContextHolder.deactivateContext();
      }

      item = mailboxItems.poll();
    }
  }

  @Override
  public @NotNull ActorPath path() {
    return this.path;
  }

  @Override
  public @NotNull <A extends Actor<M2>, M2> ActorHandle<M2> spawn(final @NotNull String name,
      final @NotNull ActorCreator<A, M2> creator) {

    if (this.state.get() != ActorState.STARTED && this.state.get() != ActorState.READY) {
      throw new IllegalStateException("Actor is not ready");
    }

    final var child = this.actorSpawner.spawn(name, creator);

    this.children.add((ActorWrapper<?>) child);

    return child;
  }

  @Override
  public void send(final @NotNull M message) {

    final var sender = activeActorContext().orElseThrow(
            () -> new IllegalStateException("Send must only be called within an active context."))
        .self();

    sendInternal(message, sender);
  }

  private @NotNull Optional<ActorContext<?>> activeActorContext() {
    return this.activeActorContextHolder.activeContext();
  }

  void sendInternal(final @NotNull M message, final @NotNull ActorHandle<?> sender) {
    appendMessage(new WrappedMessage.FireAndForgetMessage<>(message, sender.path()));
  }

  <R> @NotNull Future<R> requestResponseToInternal(final @NotNull M message,
      final @NotNull ActorHandle<?> sender) {

    final var wrapper = new MessageWithResponseRequest<M, R>(message, sender.path());

    appendMessage(wrapper);

    return wrapper.future();
  }

  <R> @NotNull RichFuture<R> requestResponseToLifecycleControlInternal(
      final @NotNull LifecycleControlMessage message, final @NotNull ActorHandle<?> sender) {

    final var wrapper = new MessageWithResponseRequest<LifecycleControlMessage, R>(message,
        sender.path());

    appendMessage(wrapper);

    return RichFuture.of(wrapper.futureInternal());
  }

  void forwardInternal(final @NotNull M message, final @NotNull ActorHandle<?> sender) {
    appendMessage(new FireAndForgetMessage<>(message, sender.path()));
  }

  void sendLifecycleControlMessage(final @NotNull LifecycleControlMessage message) {
    appendMessage(message);
  }

  private void appendMessage(final @NotNull MailboxItem mailboxItem) {
    if (this.mailboxItems.size() < 1000 || mailboxItem instanceof LifecycleControlMessage) {
      this.mailboxItems.add(mailboxItem);
    } else {
      // TODO Use overflow strategy
    }
  }

  // Visible for testing
  @NotNull ActorState state() {
    return this.state.get();
  }

  void shutdown() {
    this.messagePoller.cancel();
  }

  @Override
  public boolean equals(final Object o) {
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
