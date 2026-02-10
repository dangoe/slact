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
import de.dangoe.concurrent.slact.core.internal.MailboxItem.ActorStarted;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.ActorStopped;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.FireAndForgetMessage;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.LifecycleControlMessage;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.MessageWithResponseRequest;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.StartActor;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.StopActor;
import de.dangoe.concurrent.slact.core.internal.MailboxItem.WrappedMessage;
import de.dangoe.concurrent.slact.core.logging.ActorLogger;
import de.dangoe.concurrent.slact.core.util.concurrent.RichFuture;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

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
        final @NotNull ActorCreator<A, M1> actorCreator) {

      if (!ActorWrapper.this.isReady()) {
        throw new IllegalStateException("Actor is not ready");
      }

      final var child = ActorWrapper.this.actorSpawner.spawn(name, actorCreator);

      ActorWrapper.this.children.put(child.path(), (ActorWrapper<?>) child);

      return child;
    }

    @Override
    public <M1> @NotNull Optional<ActorHandle<M1>> resolve(final @NotNull ActorPath path) {
      return ActorWrapper.this.actorHandleResolver.resolve(path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M1> @NotNull SendMessageOp<M1> send(final @NotNull M1 message) {
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
    public <M1> @NotNull ResponseRequestOp<M1> requestResponseTo(@NotNull M1 message) {

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
    public <M1> @NotNull SendMessageOp<M1> forward(final @NotNull M1 message) {
      return targetActor -> ((ActorWrapper<M1>) targetActor).forwardInternal(message, sender());
    }

    @Override
    public @NotNull Future<Done> stop(final @NotNull ActorHandle<?> actor) {
      return ((ActorWrapper<?>) actor).requestResponseToLifecycleControlInternal(
          new StopActor(sender().path()), sender()).thenApply(it -> Done.instance());
    }
  }

  private interface ActorLogic<M> {

    boolean isReady();

    boolean processMailboxItem(@NotNull MailboxItem item, @NotNull ActorContext<M> context,
        @NotNull ActorHandle<?> sender);
  }

  private final class StartupActorLogic implements ActorLogic<M> {

    private boolean readyDuringStartup = false;

    @Override
    public boolean isReady() {
      return readyDuringStartup;
    }

    @Override
    public boolean processMailboxItem(final @NotNull MailboxItem item,
        final @NotNull ActorContext<M> context, final @NotNull ActorHandle<?> sender) {

      if (item instanceof StartActor) {

        this.readyDuringStartup = true;

        final boolean isLoggerDebugEnabled = ActorWrapper.this.logger.isDebugEnabled();

        if (isLoggerDebugEnabled) {
          logger.debug("Received start command from '{}'.", sender.path());
        }

        if (isLoggerDebugEnabled) {
          logger.debug("Invoking 'onStart' hook.");
        }

        ActorWrapper.this.delegate.onStart();

        if (isLoggerDebugEnabled) {
          logger.debug("'onStart' hook invoked.");
        }

        if (!isRootActor()) {

          if (isLoggerDebugEnabled) {
            logger.debug("Actor has been started. Notifying parent actor '{}'.",
                context.parent().path());
          }

          ((ActorWrapper<?>) context.parent()).sendLifecycleControlMessage(
              new ActorStarted(context.self().path()));
        }

        if (isLoggerDebugEnabled) {
          logger.debug("Actor is becoming ready.");
        }

        ActorWrapper.this.setBehavior(new ReadyActorLogic());

        return true;
      }

      return false;
    }
  }

  private final class ReadyActorLogic implements ActorLogic<M> {

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public boolean processMailboxItem(final @NotNull MailboxItem item,
        final @NotNull ActorContext<M> context, final @NotNull ActorHandle<?> sender) {

      final var isLoggerDebugEnabled = logger.isDebugEnabled();

      switch (item) {
        case StopActor msg -> {

          if (isLoggerDebugEnabled) {
            logger.debug("Stop requested from '{}'.", item.sender());
          }

          doStop(context);

          return true;
        }
        case ActorStopped msg -> {

          if (isLoggerDebugEnabled) {
            logger.debug("Received stop notification from '{}'.", item.sender());
          }

          ActorWrapper.this.children.remove(item.sender());
          ActorWrapper.this.stopActorFn.accept(item.sender());

          return true;
        }
        case WrappedMessage<?> wrappedMessage -> {

          try {

            if (wrappedMessage instanceof MessageWithResponseRequest<?, ?> messageWithResponseRequest) {
              //noinspection unchecked
              ActorWrapper.this.messagesWithResponseRequest.put(sender.path(),
                  (MessageWithResponseRequest<M, ?>) messageWithResponseRequest);
            }

            //noinspection unchecked
            final var message = (M) wrappedMessage.message();

            if (message instanceof StopActor) {
              doStop(context);
            } else {
              ActorWrapper.this.delegate.receiveMessage(message);
            }
          } catch (final MessageRejectedException e) {
            logger.warn("Message has been rejected.", e);
          }

          return true;
        }
        default -> {
          return false;
        }
      }
    }
  }

  private final class StoppingActorLogic implements ActorLogic<M> {

    @Override
    public boolean isReady() {
      return false;
    }

    @Override
    public boolean processMailboxItem(final @NotNull MailboxItem item,
        final @NotNull ActorContext<M> context, final @NotNull ActorHandle<?> sender) {

      if (item instanceof ActorStopped) {

        final var isLoggerDebugEnabled = logger.isDebugEnabled();

        ActorWrapper.this.children.remove(item.sender());
        ActorWrapper.this.stopActorFn.accept(item.sender());

        if (isLoggerDebugEnabled) {
          logger.debug("Child actor '{}' has been stopped.", item.sender());
        }

        if (ActorWrapper.this.children.isEmpty()) {

          if (isLoggerDebugEnabled) {
            logger.debug("No children left. Stopping actor.");
          }

          ActorWrapper.this.delegate.onStop();
          ((ActorWrapper<?>) context.parent()).sendLifecycleControlMessage(
              new ActorStopped(path()));
          // actorContext.respondWith(new StoppedMessage(path()));

          if (isLoggerDebugEnabled) {
            logger.debug("Actor has been stopped.");
          }
        }

        return true;
      }

      return false;
    }
  }

  private final @NotNull ActorLogger logger;

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
  private final @NotNull Map<ActorPath, ActorWrapper<?>> children = new ConcurrentHashMap<>();

  private @NotNull ActorWrapper.ActorLogic<M> actorLogic;

  public ActorWrapper(final @NotNull ActorLogger logger, final @NotNull Actor<M> delegate,
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

    this.actorLogic = new StartupActorLogic();
    this.activeActorContextHolder = ActiveActorContextHolder.getInstance();

    this.messagesWithResponseRequest = new HashMap<>();

    this.messagePoller = scheduledExecutor.scheduleAtFixedRate(this::processMessages,
        Duration.of(0, ChronoUnit.MILLIS), Duration.of(1, ChronoUnit.MILLIS));
  }

  private void setBehavior(final @NotNull ActorWrapper.ActorLogic<M> actorLogic) {

    if (logger.isDebugEnabled()) {
      logger.debug("Switching logic to '{}'.", actorLogic.getClass().getSimpleName());
    }

    this.actorLogic = actorLogic;
  }

  private void processMessages() {

    var item = mailboxItems.poll();

    while (item != null) {

      if (logger.isDebugEnabled()) {
        logger.debug("Processing '{}' from '{}'.", item, item.sender());
      }

      final var sender = this.actorHandleResolver.resolve(item.sender());

      if (sender.isEmpty()) {
        logger.warn("Failed to resolve sender handle for message '{}'.", item);
        break;
      }

      final var senderHandle = sender.get();
      final var actorContext = new ActorContextImpl(senderHandle);

      this.activeActorContextHolder.activateContext(actorContext);

      try {
        if (!this.actorLogic.processMailboxItem(item, actorContext, senderHandle)) {
          logger.warn("Dismissing unprocessable mailbox item: {}", item);
        }

      } catch (Exception e) {
        logger.error("Failed to process mailbox item.", e);
      } finally {
        this.activeActorContextHolder.deactivateContext();
      }

      item = mailboxItems.poll();
    }
  }

  private void doStop(final @NotNull ActorContext<M> actorContext) {

    final var isLoggerDebugEnabled = ActorWrapper.this.logger.isDebugEnabled();

    if (isLoggerDebugEnabled) {
      logger.debug("Initiating stop.");
    }

    this.setBehavior(new StoppingActorLogic());

    if (this.children.isEmpty()) {

      if (isLoggerDebugEnabled) {
        logger.debug("Actor has no children. Stopping immediately.");
      }

      if (isLoggerDebugEnabled) {
        logger.debug("Invoking 'onStop' hook.");
      }

      this.delegate.onStop();

      if (isLoggerDebugEnabled) {
        logger.debug("'onStop' hook invoked.");
      }

      if (!isRootActor()) {

        final var parent = (ActorWrapper<?>) actorContext.parent();

        if (isLoggerDebugEnabled) {
          logger.debug("Actor has been stopped. Notifying parent actor '{}'.", parent.path());
        }

        parent.sendLifecycleControlMessage(new ActorStopped(path()));
      }

    } else {

      if (isLoggerDebugEnabled) {
        logger.debug("Child actors are registered. Initiating coordinated stop.");
      }

      this.children.values().forEach(
          child -> child.sendLifecycleControlMessage(new StopActor(actorContext.self().path())));
    }
  }

  @Override
  public @NotNull ActorPath path() {
    return this.path;
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

  private boolean isRootActor() {
    return Objects.equals(path(), ActorPath.root());
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
  boolean isReady() {
    return this.actorLogic.isReady();
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
