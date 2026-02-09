package de.dangoe.concurrent.slact.testkit;

import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorCreator;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.ActorPath;
import de.dangoe.concurrent.slact.core.Done;
import de.dangoe.concurrent.slact.core.FuturePipeOp;
import de.dangoe.concurrent.slact.core.SlactContainer;
import de.dangoe.concurrent.slact.core.SlactContainerBuilder;
import de.dangoe.concurrent.slact.core.internal.ActorState;
import de.dangoe.concurrent.slact.core.internal.ActorStateReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

  public @NotNull <M> SendMessageOp<M> sendMultiple(final @NotNull Iterable<M> messages) {
    return targetActor -> {
      for (final var message : messages) {
        send(message).to(targetActor);
      }
    };
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

  public void awaitReady(final @NotNull ActorPath path, final @NotNull ActorPath... paths) {

    final var pathsLists = new ArrayList<>(Arrays.asList(paths));
    pathsLists.add(path);

    await().atMost(Duration.ofSeconds(5))
        .until(() -> pathsLists.stream().allMatch(it -> getActorState(it) == ActorState.READY));
  }

  public @NotNull ActorState getActorState(final @NotNull ActorPath path) {
    return new ActorStateReader(this).readState(path);
  }
}
