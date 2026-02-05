package de.dangoe.concurrent.slact.testsupport;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorCreator;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.Done;
import de.dangoe.concurrent.slact.core.FuturePipeOp;
import de.dangoe.concurrent.slact.core.SlactContainer;
import de.dangoe.concurrent.slact.core.SlactContainerBuilder;
import java.util.Optional;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public final class SlactTestContainer implements SlactContainer {

  private final @NotNull SlactContainer delegate;

  public SlactTestContainer() {
    this.delegate = new SlactContainerBuilder().build();
  }

  @Override
  public void close() throws Exception {
    delegate.close();
  }

  @Override
  public void shutdown() throws Exception {
    delegate.shutdown();
  }

  @Override
  public boolean isStopped() {
    return delegate.isStopped();
  }

  @Override
  public @NotNull Future<Done> stop(final @NotNull ActorHandle<?> actor) {
    return delegate.stop(actor);
  }

  @Override
  public @NotNull <M> SendMessageOp<M> send(final @NotNull M message) {
    return delegate.send(message);
  }

  @Override
  public @NotNull <M> SendMessageOp<M> forward(final @NotNull M message) {
    return delegate.forward(message);
  }

  @Override
  public @NotNull <M> ResponseRequestOp<M> requestResponseTo(final @NotNull M message) {
    return delegate.requestResponseTo(message);
  }

  @Override
  public @NotNull <M1> FuturePipeOp<M1> pipeFuture(final @NotNull Future<M1> eventualMessage) {
    return delegate.pipeFuture(eventualMessage);
  }

  @Override
  public @NotNull <M> Optional<ActorHandle<M>> resolve(final @NotNull ActorPath path) {
    return delegate.resolve(path);
  }

  @Override
  public @NotNull <A extends Actor<M>, M> ActorHandle<M> spawn(
      final @NotNull ActorCreator<A, M> actorCreator) {
    return delegate.spawn(actorCreator);
  }

  @Override
  public @NotNull <A extends Actor<M>, M> ActorHandle<M> spawn(final @NotNull String name,
      @NotNull ActorCreator<A, M> actorCreator) {
    return delegate.spawn(name, actorCreator);
  }
}
