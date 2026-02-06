package de.dangoe.concurrent.slact.core;

import static de.dangoe.concurrent.slact.core.testhelper.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.patterns.actors.FailingOnReceiveActor;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
class CoordinatedActorStopTest {

  private static abstract class CoordinatedActorStopBehaviour {

    private final AtomicBoolean onStopCalled = new AtomicBoolean();
    private final AtomicReference<Future<Done>> eventualStopResult = new AtomicReference<Future<Done>>();

    protected abstract @NotNull List<ActorHandle<?>> spawnChildActors(
        final @NotNull ActorContext<?> actorContext);

    @Nested
    @DisplayName("Should be stopped in a coordinated way")
    class ShouldBeStoppedInACoordinatedWay {

      @Test
      @DisplayName("When stopped via container")
      void whenStoppedViaContainer(final @NotNull SlactTestContainer container) {

        final var actor = container.spawn("actor", () -> new FailingOnReceiveActor<>() {

          @Override
          public void onStart() {
            super.onStart();
            spawnChildActors(context());
          }

          @Override
          public void onStop() {
            super.onStop();
            onStopCalled.set(true);
          }
        });

        eventualStopResult.set(container.stop(actor));

        verifyStopped(container, actor);
      }

      @Test
      @DisplayName("When stopped from another actor")
      void whenStoppedFromAnotherActor(final @NotNull SlactTestContainer container) {

        final var actor = container.spawn("actor", () -> new FailingOnReceiveActor<>() {

          @Override
          public void onStart() {
            super.onStart();
            spawnChildActors(context());
          }

          @Override
          public void onStop() {
            super.onStop();
            onStopCalled.set(true);
          }
        });

        container.spawn("stopping-actor", () -> new FailingOnReceiveActor<>() {

          @Override
          public void onStart() {
            super.onStart();
            eventualStopResult.set(context().stop(actor));
          }
        });

        verifyStopped(container, actor);
      }

      @Test
      @DisplayName("When stopped from inside of actor")
      void whenStoppedFromInsideActor(final @NotNull SlactTestContainer container) {

        final var actor = container.spawn("actor", () -> new FailingOnReceiveActor<>() {

          @Override
          public void onStart() {
            super.onStart();
            spawnChildActors(context());
            eventualStopResult.set(context().stop(self()));
          }

          @Override
          public void onStop() {
            super.onStop();
            onStopCalled.set(true);
          }
        });

        verifyStopped(container, actor);
      }

      private void verifyStopped(@NotNull SlactTestContainer container, ActorHandle<Object> actor) {

        await().atMost(DEFAULT_TIMEOUT)
            .untilAsserted(() -> assertThat(eventualStopResult.get()).isDone());

        assertThat(onStopCalled).isTrue();
        assertThat(container.resolve(actor.path())).isEmpty();
      }
    }
  }

  @Nested
  @DisplayName("An actor without children")
  class AnActorWithoutChildren extends CoordinatedActorStopBehaviour {

    @Override
    protected @NotNull List<ActorHandle<?>> spawnChildActors(
        final @NotNull ActorContext<?> actorContext) {

      return List.of();
    }
  }

  @Nested
  @DisplayName("An actor with children but no grandchildren")
  class AnActorWithChildrenButNoGrandchildren extends CoordinatedActorStopBehaviour {

    @Override
    protected @NotNull List<ActorHandle<?>> spawnChildActors(
        final @NotNull ActorContext<?> actorContext) {

      return List.of(actorContext.spawn("first-child", () -> new FailingOnReceiveActor<>()),
          actorContext.spawn("second-child", () -> new FailingOnReceiveActor<>()));
    }
  }

  @Nested
  @DisplayName("An actor with children and grandchildren")
  class AnActorWithChildrenAndGrandchildren extends CoordinatedActorStopBehaviour {

    @Override
    protected @NotNull List<ActorHandle<?>> spawnChildActors(
        final @NotNull ActorContext<?> actorContext) {

      return List.of(actorContext.spawn("first-child", () -> new FailingOnReceiveActor<>() {

        @Override
        public void onStart() {
          super.onStart();
          context().spawn("first-child-first-grandchild", () -> new FailingOnReceiveActor<>());
          context().spawn("first-child-second-grandchild", () -> new FailingOnReceiveActor<>());
        }
      }), actorContext.spawn("second-child", () -> new FailingOnReceiveActor<>() {

        @Override
        public void onStart() {
          super.onStart();
          context().spawn("second-child-first-grandchild", () -> new FailingOnReceiveActor<>());
          context().spawn("second-child-second-grandchild", () -> new FailingOnReceiveActor<>());
        }
      }));
    }
  }
}
