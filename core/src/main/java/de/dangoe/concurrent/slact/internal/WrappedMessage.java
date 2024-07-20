package de.dangoe.concurrent.slact.internal;

import de.dangoe.concurrent.slact.ActorPath;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public abstract class WrappedMessage<M> {

  public static final class FireAndForgetMessage<M> extends WrappedMessage<M> {

    public FireAndForgetMessage(final M message, final String correlationMessageId,
        final ActorPath sender) {
      super(message, correlationMessageId, sender);
    }
  }

  public static final class MessageWithResponseRequest<M, R> extends WrappedMessage<M> {

    private final CompletableFuture<R> future;

    public MessageWithResponseRequest(final M message, final String correlationMessageId, final ActorPath sender) {
      super(message, correlationMessageId, sender);
      this.future = new CompletableFuture<>();
    }

    public Future<R> future() {
      return future;
    }

    public CompletableFuture<R> futureInternal() {
      return future;
    }
  }

  private final String messageId;
  private final String correlationMessageId;

  private final M message;

  private final ActorPath sender;

  protected WrappedMessage(final M message, final String correlationMessageId,
      final ActorPath sender) {
    super();

    this.messageId = UUID.randomUUID().toString();
    this.correlationMessageId = correlationMessageId;

    this.message = message;

    this.sender = sender;
  }

  public String messageId() {
    return messageId;
  }

  public Optional<String> correlationMessageId() {
    return Optional.ofNullable(correlationMessageId);
  }

  public final M message() {
    return message;
  }

  public final ActorPath sender() {
    return sender;
  }
}
