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

public final class RoutingActor<R extends RoutingRequest<M>, M> extends Actor<R> {

  public interface RoutingRequest<M> {

    M message();
  }

  public record SimpleRoutingRequest<M>(@NotNull M message) implements RoutingRequest<M> {

  }

  public interface RoutingStrategy<R, M> {

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

  public static <R extends RoutingRequest<M>, M> @NotNull ActorCreator<RoutingActor<R, M>, R> custom(
      final @NotNull Function<ActorContext<R>, RoutingStrategy<R, M>> strategyFactory) {

    return () -> new RoutingActor<>(strategyFactory);
  }

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
