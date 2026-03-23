package de.dangoe.concurrent.slact.core;

import static de.dangoe.concurrent.slact.testkit.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.patterns.actors.FailingOnReceiveActor;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
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

  private static abstract class CoordinatedActorStopBehavior {

    private final @NotNull AtomicReference<Future<Done>> eventualStopResult = new AtomicReference<>();
    private final @NotNull List<ActorPath> stopOrder = new CopyOnWriteArrayList<>();

    protected final @NotNull Consumer<ActorPath> markAsStoppedConsumer = stopOrder::add;

    protected abstract void spawnChildActors(final @NotNull ActorContext<?> actorContext);

    protected abstract void verifyStoppedInternal(final @NotNull List<ActorPath> stoppedActorPaths);

    protected final void verifyCoordinatedStop(final @NotNull ActorPath parentPath,
        final @NotNull Iterable<ActorPath> childrenPaths) {

      await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {

        verifyUnresolvable(parentPath);

        final var parentIndex = stopOrder.indexOf(parentPath);

        assertThat(parentIndex).describedAs("Expected parent actor '%s' to be stopped", parentPath)
            .isNotNegative();

        for (final var childPath : childrenPaths) {

          verifyUnresolvable(childPath);

          final var childIndex = stopOrder.indexOf(childPath);

          assertThat(childIndex).describedAs("Expected child actor '%s' to be stopped", childPath)
              .isNotNegative();
          assertThat(childIndex).describedAs(
              "Expected child actor '%s' to be stopped before parent '%s'.".formatted(childPath,
                  parentPath)).isLessThan(parentIndex);
        }
      });
    }

    // Initialized by JUnit
    @SuppressWarnings("NotNullFieldNotInitialized")
    private @NotNull SlactTestContainer container;

    @BeforeEach
    void beforeEach(final @NotNull SlactTestContainer container) {
      this.container = container;
    }

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

        container.awaitReady(actor.path());

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

        container.awaitReady(actor.path());

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

        verifyStoppedInternal(stopOrder);
        verifyUnresolvable(actor.path());
      }
    }

    private void verifyUnresolvable(final @NotNull ActorPath path) {
      assertThat(container.resolve(path)).describedAs(
              "Expected actor '%s' to be stopped and not resolvable anymore.".formatted(path))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("When stopping an already-stopped actor")
  class WhenStoppingAnAlreadyStoppedActor {

    @Test
    @DisplayName("when stop is called a second time, then no exception is thrown and the actor remains unresolvable")
    void whenStopIsCalledASecondTime_thenNoExceptionIsThrownAndActorRemainsUnresolvable(
        final @NotNull SlactTestContainer container) {

      final var actor = container.spawn("actor", FailingOnReceiveActor::new);

      container.awaitReady(actor.path());

      final var firstStop = container.stop(actor);

      await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> assertThat(firstStop).isDone());

      assertThatNoException().isThrownBy(() -> container.stop(actor));
      assertThat(container.resolve(actor.path()))
          .describedAs("Actor should remain unresolvable after a second stop call")
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("An actor without children")
  class AnActorWithoutChildren extends CoordinatedActorStopBehavior {

    @Override
    protected void spawnChildActors(final @NotNull ActorContext<?> actorContext) {
      // Nothing to do
    }

    @Override
    protected void verifyStoppedInternal(final @NotNull List<ActorPath> stoppedActorPaths) {

      await().atMost(DEFAULT_TIMEOUT).untilAsserted(
          () -> assertThat(stoppedActorPaths).usingRecursiveFieldByFieldElementComparator()
              .containsExactly(ActorPath.root().append("actor")));
    }
  }

  @Nested
  @DisplayName("An actor with children but no grandchildren")
  class AnActorWithChildrenButNoGrandchildren extends CoordinatedActorStopBehavior {

    @Override
    protected void spawnChildActors(final @NotNull ActorContext<?> actorContext) {

      actorContext.spawn("first-child", () -> new StopTrackingActor(markAsStoppedConsumer));
      actorContext.spawn("second-child", () -> new StopTrackingActor(markAsStoppedConsumer));
    }

    @Override
    protected void verifyStoppedInternal(final @NotNull List<ActorPath> stoppedActorPaths) {

      final var parentActorPath = ActorPath.root().append("actor");

      verifyCoordinatedStop(parentActorPath,
          Set.of(parentActorPath.append("first-child"), parentActorPath.append("second-child")));
    }
  }

  @Nested
  @DisplayName("An actor with children and grandchildren")
  class AnActorWithChildrenAndGrandchildren extends CoordinatedActorStopBehavior {

    @Override
    protected void spawnChildActors(final @NotNull ActorContext<?> actorContext) {

      actorContext.spawn("first-child", () -> new StopTrackingActor(markAsStoppedConsumer) {

        @Override
        public void onStart() {
          super.onStart();
          context().spawn("first-grandchild", () -> new StopTrackingActor(markAsStoppedConsumer));
          context().spawn("second-grandchild", () -> new StopTrackingActor(markAsStoppedConsumer));
        }
      });

      actorContext.spawn("second-child", () -> new StopTrackingActor(markAsStoppedConsumer) {

        @Override
        public void onStart() {
          super.onStart();
          context().spawn("first-grandchild", () -> new StopTrackingActor(markAsStoppedConsumer));
          context().spawn("second-grandchild", () -> new StopTrackingActor(markAsStoppedConsumer));
        }
      });
    }

    @Override
    protected void verifyStoppedInternal(final @NotNull List<ActorPath> stoppedActorPaths) {

      final var parentActorPath = ActorPath.root().append("actor");
      final var firstChildPath = parentActorPath.append("first-child");
      final var secondChildPath = parentActorPath.append("second-child");

      verifyCoordinatedStop(firstChildPath, Set.of(firstChildPath.append("first-grandchild"),
          firstChildPath.append("second-grandchild")));
      verifyCoordinatedStop(secondChildPath, Set.of(secondChildPath.append("first-grandchild"),
          secondChildPath.append("second-grandchild")));
      verifyCoordinatedStop(parentActorPath, Set.of(firstChildPath, secondChildPath));
    }
  }
}
