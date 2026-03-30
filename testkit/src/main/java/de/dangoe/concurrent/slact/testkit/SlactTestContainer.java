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
import de.dangoe.concurrent.slact.core.internal.ActorReadinessResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Test container for managing actors and their lifecycle in tests.
 */
public final class SlactTestContainer implements SlactContainer {

  private final @NotNull SlactContainer delegate;

  /**
   * Creates a new test container.
   */
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

  /**
   * Sends multiple messages to actors.
   *
   * @param messages iterable of messages.
   * @param <M>      the message type.
   * @return an operation that sends all messages to the target in order.
   */
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

  /**
   * Waits until the given actor path and additional paths are ready.
   *
   * @param path  the main actor path.
   * @param paths additional actor paths.
   */
  public void awaitReady(final @NotNull ActorPath path, final @NotNull ActorPath... paths) {

    final var pathsLists = new ArrayList<>(Arrays.asList(paths));
    pathsLists.add(path);

    awaitReady(pathsLists);
  }

  /**
   * Waits until all given actor paths are ready.
   *
   * @param paths the actor paths to wait for.
   */
  public void awaitReady(final @NotNull Iterable<ActorPath> paths) {

    await().atMost(Constants.DEFAULT_TIMEOUT)
        .until(
            () -> StreamSupport.stream(paths.spliterator(), true).allMatch(this::isReady));
  }

  /**
   * Waits until all given actor paths have completed startup.
   *
   * @param paths the actor paths to wait for.
   */
  public void awaitStartupComplete(final @NotNull Iterable<ActorPath> paths) {

    await().atMost(Constants.DEFAULT_TIMEOUT)
        .until(
            () -> StreamSupport.stream(paths.spliterator(), true)
                .allMatch(this::isStartupComplete));
  }

  /**
   * Checks if the given actor path is ready.
   *
   * @param path the actor path.
   * @return {@code true} if the actor is ready, {@code false} otherwise.
   */
  public boolean isReady(final @NotNull ActorPath path) {
    return new ActorReadinessResolver(this).isReady(path);
  }

  /**
   * Checks if the given actor path has completed startup.
   *
   * @param path the actor path.
   * @return {@code true} if startup is complete, {@code false} otherwise.
   */
  public boolean isStartupComplete(final @NotNull ActorPath path) {
    return new ActorReadinessResolver(this).isStartupComplete(path);
  }
}
