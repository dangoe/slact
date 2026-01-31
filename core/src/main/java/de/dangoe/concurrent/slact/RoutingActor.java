package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.RoutingActor.RoutingRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    @NotNull ActorHandle<? extends M> nextRoutingTarget(
        @NotNull RoutingRequest<? extends M> request);
  }

  private static final class RoundRobinRoutingStrategy<M> implements RoutingStrategy<M> {

    private final @NotNull List<ActorHandle<? extends M>> targetActorHandles;
    private int nextTargetActorIndex;

    private RoundRobinRoutingStrategy(
        final @NotNull List<ActorHandle<? extends M>> targetActorHandles) {

      if (targetActorHandles.isEmpty()) {
        throw new IllegalArgumentException("No routee actor handles found");
      }

      this.targetActorHandles = List.copyOf(targetActorHandles);
      this.nextTargetActorIndex = 0;
    }

    @Override
    public @NotNull ActorHandle<? extends M> nextRoutingTarget(
        final @NotNull RoutingRequest<? extends M> request) {
      final var targetActorHandle = this.targetActorHandles.get(this.nextTargetActorIndex);
      this.nextTargetActorIndex = (this.nextTargetActorIndex + 1) % targetActorHandles.size();
      return targetActorHandle;
    }
  }

  private final @NotNull Function<ActorContext, RoutingStrategy<M>> strategyFactory;

  private @Nullable RoutingStrategy<M> strategy;

  public RoutingActor(final @NotNull Function<ActorContext, RoutingStrategy<M>> strategyFactory) {
    this.strategyFactory = strategyFactory;
  }

  @Override
  public void onMessage(final @NotNull RoutingRequest<M> request) {

    if (this.strategy == null) {
      this.strategy = this.strategyFactory.apply(context());
    }

    final var target = this.strategy.nextRoutingTarget(request);
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
