package de.dangoe.concurrent.slact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// TODO Add tests for child propagation
class ActorStoppingTest {

  private static class TestActor extends Actor<String> {

    @Override
    public void onMessage(@NotNull String message) {
      throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
    }
  }

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final SlactContainer container = new SlactContainerBuilder().build();

  @Nested
  class ActorsCanBeStopped {

    @Test
    void whenUsingContainer() throws Throwable {

      final var onStopCalled = new AtomicBoolean();

      final var actorToBeStopped = container.spawn("actor-to-be-stopped", () -> new TestActor() {

        @Override
        void onStop() {
          super.onStop();
          onStopCalled.set(true);
        }
      });

      final var eventualStopResult = container.stop(actorToBeStopped);

      await().atMost(TIMEOUT).until(eventualStopResult::isDone);

      assertThat(eventualStopResult.get()).isSameAs(Done.instance());
      assertThat(onStopCalled).isTrue();
      assertThat(container.resolve(actorToBeStopped.path())).isEmpty();
    }

    @Test
    void whenFromInsideActor() throws Throwable {

      final var onStopCalled = new AtomicBoolean();
      final var eventualStopResult = new AtomicReference<Future<Done>>();

      final var actorToBeStopped = container.spawn("actor-to-be-stopped", () -> new TestActor() {

        @Override
        void onStart() {
          super.onStart();
          eventualStopResult.set(context().stop(self()));
        }

        @Override
        void onStop() {
          super.onStop();
          onStopCalled.set(true);
        }
      });

      await().atMost(TIMEOUT)
          .until(() -> eventualStopResult.get() != null && eventualStopResult.get().isDone());

      assertThat(eventualStopResult.get().get()).isSameAs(Done.instance());
      assertThat(onStopCalled).isTrue();
      assertThat(container.resolve(actorToBeStopped.path())).isEmpty();
    }

    @Test
    void whenFromInsideAnotherActor() throws Throwable {

      final var onStopCalled = new AtomicBoolean();

      final var actorToBeStopped = container.spawn("actor-to-be-stopped",
          () -> new Actor<String>() {

            @Override
            public void onMessage(final @NotNull String message) {
              throw new UnsupportedOperationException();
            }

            @Override
            void onStop() {
              super.onStop();
              onStopCalled.set(true);
            }
          });

      final var eventualStopResult = new AtomicReference<Future<Done>>();

      container.spawn("actor", () -> new TestActor() {

        @Override
        void onStart() {
          super.onStart();
          eventualStopResult.set(context().stop(actorToBeStopped));
        }
      });

      await().atMost(TIMEOUT)
          .until(() -> eventualStopResult.get() != null && eventualStopResult.get().isDone());

      assertThat(eventualStopResult.get().get()).isSameAs(Done.instance());
      assertThat(onStopCalled).isTrue();
      assertThat(container.resolve(actorToBeStopped.path())).isEmpty();
    }
  }
}
