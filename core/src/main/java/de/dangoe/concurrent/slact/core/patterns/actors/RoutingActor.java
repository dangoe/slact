package de.dangoe.concurrent.slact.core.patterns.actors;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorContext;
import de.dangoe.concurrent.slact.core.ActorCreator;
import de.dangoe.concurrent.slact.core.ActorHandle;
import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor.RoutingRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Routes incoming {@link RoutingRequest} messages to child actors according to a pluggable
 * {@link RoutingStrategy}. Use {@link #roundRobinWorker} for the built-in round-robin strategy, or
 * {@link #custom} to supply your own.
 *
 * @param <R> The routing-request type.
 * @param <M> The message type dispatched to the routee actors.
 */
public final class RoutingActor<R extends RoutingRequest<M>, M> extends Actor<R> {

  /**
   * Wraps a message together with routing metadata so the router can dispatch it to a routee.
   *
   * @param <M> the message type.
   */
  public interface RoutingRequest<M> {

    M message();
  }

  /**
   * A minimal {@link RoutingRequest} that carries only the message payload.
   *
   * @param message the message to route.
   * @param <M>     the message type.
   */
  public record SimpleRoutingRequest<M>(@NotNull M message) implements RoutingRequest<M> {

  }

  /**
   * Strategy that selects which routee actor should receive the next message.
   *
   * @param <R> the routing-request type.
   * @param <M> the message type.
   */
  public interface RoutingStrategy<R, M> {

    /**
     * Selects the routee actor handle for the given request.
     *
     * @param request the routing request containing the message and any routing metadata.
     * @return the handle of the actor that should receive the message.
     */
    @NotNull ActorHandle<? extends M> selectRoutee(@NotNull R request);
  }

  private static final class RoundRobinRoutingStrategy<M> implements
      RoutingStrategy<SimpleRoutingRequest<M>, M> {

    private final @NotNull List<ActorHandle<? extends M>> routeeHandles;
    private int nextRouteeIndex;

    private RoundRobinRoutingStrategy(final @NotNull List<ActorHandle<? extends M>> routeeHandles) {

      if (routeeHandles.isEmpty()) {
        throw new IllegalArgumentException("No routee actor handles found");
      }

      this.routeeHandles = List.copyOf(routeeHandles);
      this.nextRouteeIndex = 0;
    }

    @Override
    public @NotNull ActorHandle<? extends M> selectRoutee(
        final @NotNull SimpleRoutingRequest<M> request) {
      final var routeeHandle = this.routeeHandles.get(this.nextRouteeIndex);
      this.nextRouteeIndex = (this.nextRouteeIndex + 1) % routeeHandles.size();
      return routeeHandle;
    }
  }

  private final @NotNull Function<ActorContext<R>, RoutingStrategy<R, M>> strategyFactory;

  // Initialized in onStart
  @SuppressWarnings("NotNullFieldNotInitialized")
  private @NotNull RoutingStrategy<R, M> strategy;

  private RoutingActor(
      final @NotNull Function<ActorContext<R>, RoutingStrategy<R, M>> strategyFactory) {
    this.strategyFactory = strategyFactory;
  }

  @Override
  public void onStart() {
    this.strategy = this.strategyFactory.apply(context());
  }

  @Override
  public void onMessage(final @NotNull R request) {
    final var target = this.strategy.selectRoutee(request);
    context().forward(request.message()).to(target);
  }

  /**
   * Creates a {@link RoutingActor} whose strategy is produced by the given factory. The factory
   * receives the actor context so it can spawn routee children.
   *
   * @param strategyFactory factory that creates the {@link RoutingStrategy} once the actor starts.
   * @param <R>             the routing-request type.
   * @param <M>             the message type dispatched to routees.
   * @return an {@link ActorCreator} for the new router.
   */
  public static <R extends RoutingRequest<M>, M> @NotNull ActorCreator<RoutingActor<R, M>, R> custom(
      final @NotNull Function<ActorContext<R>, RoutingStrategy<R, M>> strategyFactory) {

    return () -> new RoutingActor<>(strategyFactory);
  }

  /**
   * Creates a {@link RoutingActor} that spawns {@code executorsCount} child actors and routes
   * {@link SimpleRoutingRequest} messages to them in round-robin order.
   *
   * @param executorsCount the number of worker actors to spawn.
   * @param actorCreator   creator for each worker actor.
   * @param <A>            the worker actor type.
   * @param <M>            the worker message type.
   * @return an {@link ActorCreator} for the new round-robin router.
   */
  public static <A extends Actor<M>, M> @NotNull ActorCreator<RoutingActor<SimpleRoutingRequest<M>, M>, SimpleRoutingRequest<M>> roundRobinWorker(
      int executorsCount, final @NotNull ActorCreator<A, M> actorCreator) {

    return () -> new RoutingActor<>(context -> {

      final var workerActorHandles = new ArrayList<ActorHandle<? extends M>>();

      for (int i = 0; i < executorsCount; i++) {
        workerActorHandles.add(context.spawn(actorCreator));
      }

      return new RoundRobinRoutingStrategy<>(workerActorHandles);
    });
  }
}
