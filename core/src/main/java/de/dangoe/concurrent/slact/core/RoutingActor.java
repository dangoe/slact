package de.dangoe.concurrent.slact.core;

import de.dangoe.concurrent.slact.core.RoutingActor.RoutingRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

public final class RoutingActor<M> extends Actor<RoutingRequest<M>> {

  public interface RoutingRequest<M> {

    M message();
  }

  public record SimpleRoutingRequest<M>(@NotNull M message) implements RoutingRequest<M> {

  }

  public record ParameterizedRoutingRequest<M, P>(@NotNull M message, @NotNull P params) implements
      RoutingRequest<M> {

  }

  public sealed interface RoutingStrategy<M> permits RoundRobinRoutingStrategy {

    @NotNull ActorHandle<? extends M> selectRoutee(@NotNull RoutingRequest<? extends M> request);
  }

  private static final class RoundRobinRoutingStrategy<M> implements RoutingStrategy<M> {

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
        final @NotNull RoutingRequest<? extends M> request) {
      final var routeeHandle = this.routeeHandles.get(this.nextRouteeIndex);
      this.nextRouteeIndex = (this.nextRouteeIndex + 1) % routeeHandles.size();
      return routeeHandle;
    }
  }

  private final @NotNull Function<ActorContext<RoutingRequest<M>>, RoutingStrategy<M>> strategyFactory;

  // Initialized in onStart
  @SuppressWarnings("NotNullFieldNotInitialized")
  private @NotNull RoutingStrategy<M> strategy;

  public RoutingActor(
      final @NotNull Function<ActorContext<RoutingRequest<M>>, RoutingStrategy<M>> strategyFactory) {
    this.strategyFactory = strategyFactory;
  }

  @Override
  public void onStart() {
    this.strategy = this.strategyFactory.apply(context());
  }

  @Override
  public void onMessage(final @NotNull RoutingRequest<M> request) {
    final var target = this.strategy.selectRoutee(request);
    context().forward(request.message()).to(target);
  }

  public static <A extends Actor<M>, M> @NotNull ActorCreator<RoutingActor<M>, RoutingRequest<M>> roundRobinWorker(
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
