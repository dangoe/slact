package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.DistributingActor.Protocol;
import de.dangoe.concurrent.slact.DistributingActor.Protocol.DistributionMethod;
import de.dangoe.concurrent.slact.DistributingActor.Protocol.Message.Distribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class DistributingActor<A extends Actor<M>, M> extends Actor<Protocol.Message> {

  public interface Protocol {

    enum DistributionMethod {
      ROUND_ROBIN
    }

    sealed interface Message permits Message.Initialize, Message.Distribute {

      record Initialize(@NotNull DistributingActor.Protocol.DistributionMethod method) implements Message {

        public Initialize {
          Objects.requireNonNull(method, "Method must not be null!");
        }
      }

      record Distribute<M>(@NotNull M message) implements Message {

      }
    }
  }

  private final class RoundRobinMessageReceiver implements MessageReceiver<Protocol.Message> {

    private final List<ActorHandle<? extends M>> childActorHandles;

    private RoundRobinMessageReceiver(
        final @NotNull List<ActorHandle<? extends M>> childActorHandles) {
      this.childActorHandles = List.copyOf(childActorHandles);
    }

    @Override
    public void onMessage(final @NotNull DistributingActor.Protocol.Message message) {

      if (message instanceof Distribute<?>(Object rawMessage)) {

        for (final var actorHandle : childActorHandles) {
          //noinspection unchecked
          context().send((M) rawMessage).to(actorHandle);
        }
      }
    }
  }

  private final int executorsCount;
  private final @NotNull ActorCreator<A, M> actorCreator;

  public DistributingActor(final int executorsCount,
      final @NotNull ActorCreator<A, M> actorCreator) {
    this.executorsCount = executorsCount;
    this.actorCreator = actorCreator;
  }

  @Override
  public void onMessage(final @NotNull DistributingActor.Protocol.Message message) {

    if (message instanceof Protocol.Message.Initialize(DistributionMethod method)) {

      final var childActorHandles = new ArrayList<ActorHandle<? extends M>>();

      for (int i = 0; i < this.executorsCount; i++) {
        childActorHandles.add(context().spawn(actorCreator));
      }

      if (method == DistributionMethod.ROUND_ROBIN) {
        behaveAs(new RoundRobinMessageReceiver(childActorHandles));
      }
    }
  }
}
