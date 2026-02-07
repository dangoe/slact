package de.dangoe.concurrent.slact.core;

import static de.dangoe.concurrent.slact.core.testhelper.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.core.internal.ActorState;
import de.dangoe.concurrent.slact.core.internal.ActorStateReader;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.patterns.actors.FailingOnReceiveActor;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
class CoordinatedActorStopTest {

  private static class StopTrackingActor extends FailingOnReceiveActor<Object> {

    private final @NotNull Consumer<ActorPath> stopTracker;

    private StopTrackingActor(final @NotNull Consumer<ActorPath> stopTracker) {
      this.stopTracker = stopTracker;
    }

    @Override
    public void onStop() {
      super.onStop();
      this.stopTracker.accept(self().path());
    }
  }

  private static abstract class CoordinatedActorStopBehaviour {

    private final @NotNull AtomicReference<Future<Done>> eventualStopResult = new AtomicReference<>();
    private final @NotNull List<ActorPath> stopOrder = new CopyOnWriteArrayList<>();

    protected final @NotNull Consumer<ActorPath> markAsStoppedConsumer = stopOrder::add;

    protected abstract void spawnChildActors(final @NotNull ActorContext<?> actorContext);

    protected abstract void verifyStopOrder(final @NotNull List<ActorPath> stopOrder);

    @Nested
    @DisplayName("Should be stopped in a coordinated way")
    class ShouldBeStoppedInACoordinatedWay {

      @Test
      @DisplayName("When stopped via container")
      void whenStoppedViaContainer(final @NotNull SlactTestContainer container) {

        final var actor = container.spawn("actor",
            () -> new StopTrackingActor(markAsStoppedConsumer) {

              @Override
              public void onStart() {
                super.onStart();
                spawnChildActors(context());
              }
            });

        eventualStopResult.set(container.stop(actor));

        verifyStopped(actor);
      }

      @Test
      @DisplayName("When stopped from another actor")
      void whenStoppedFromAnotherActor(final @NotNull SlactTestContainer container) {

        final var actor = container.spawn("actor",
            () -> new StopTrackingActor(markAsStoppedConsumer) {

              @Override
              public void onStart() {
                super.onStart();
                spawnChildActors(context());
              }
            });

        container.spawn("stopping-actor", () -> new FailingOnReceiveActor<>() {

          @Override
          public void onStart() {
            super.onStart();
            eventualStopResult.set(context().stop(actor));
          }
        });

        verifyStopped(actor);
      }

      @Test
      @DisplayName("When stopped from inside of actor")
      void whenStoppedFromInsideActor(final @NotNull SlactTestContainer container) {

        final var actor = container.spawn("actor",
            () -> new StopTrackingActor(markAsStoppedConsumer) {

              @Override
              public void onStart() {
                super.onStart();
                spawnChildActors(context());
                eventualStopResult.set(context().stop(self()));
              }
            });

        verifyStopped(actor);
      }

      private void verifyStopped(final @NotNull ActorHandle<Object> actor) {

        await().atMost(DEFAULT_TIMEOUT)
            .untilAsserted(() -> assertThat(eventualStopResult.get()).isDone());

        assertThat(ActorStateReader.readState(actor)).isEqualTo(ActorState.STOPPED);

        verifyStopOrder(stopOrder);
      }
    }
  }

  @Nested
  @DisplayName("An actor without children")
  class AnActorWithoutChildren extends CoordinatedActorStopBehaviour {

    @Override
    protected void spawnChildActors(final @NotNull ActorContext<?> actorContext) {

      // Nothing to do
    }

    @Override
    protected void verifyStopOrder(final @NotNull List<ActorPath> stopOrder) {

      assertThat(stopOrder).usingRecursiveFieldByFieldElementComparator()
          .containsExactly(ActorPath.root().append("actor"));
    }
  }

  @Nested
  @DisplayName("An actor with children but no grandchildren")
  class AnActorWithChildrenButNoGrandchildren extends CoordinatedActorStopBehaviour {

    @Override
    protected void spawnChildActors(final @NotNull ActorContext<?> actorContext) {

      actorContext.spawn("first-child", () -> new FailingOnReceiveActor<>());
      actorContext.spawn("second-child", () -> new FailingOnReceiveActor<>());
    }

    @Override
    protected void verifyStopOrder(final @NotNull List<ActorPath> stopOrder) {

      assertThat(stopOrder).usingRecursiveFieldByFieldElementComparator()
          .containsExactly(ActorPath.root().append("actor"));
    }
  }

  @Nested
  @DisplayName("An actor with children and grandchildren")
  class AnActorWithChildrenAndGrandchildren extends CoordinatedActorStopBehaviour {

    @Override
    protected void spawnChildActors(final @NotNull ActorContext<?> actorContext) {

      actorContext.spawn("first-child", () -> new FailingOnReceiveActor<>() {

        @Override
        public void onStart() {
          super.onStart();
          context().spawn("first-child-first-grandchild",
              () -> new StopTrackingActor(markAsStoppedConsumer));
          context().spawn("first-child-second-grandchild",
              () -> new StopTrackingActor(markAsStoppedConsumer));
        }
      });

      actorContext.spawn("second-child", () -> new StopTrackingActor(markAsStoppedConsumer) {

        @Override
        public void onStart() {
          super.onStart();
          context().spawn("second-child-first-grandchild",
              () -> new StopTrackingActor(markAsStoppedConsumer));
          context().spawn("second-child-second-grandchild",
              () -> new StopTrackingActor(markAsStoppedConsumer));
        }
      });
    }

    @Override
    protected void verifyStopOrder(final @NotNull List<ActorPath> stopOrder) {

      assertThat(stopOrder).usingRecursiveFieldByFieldElementComparator()
          .containsExactly(ActorPath.root().append("actor"));
    }
  }
}
