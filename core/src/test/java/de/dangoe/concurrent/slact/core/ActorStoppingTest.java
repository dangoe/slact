package de.dangoe.concurrent.slact.core;

import static de.dangoe.concurrent.slact.core.testhelper.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// TODO Add tests for child propagation
@ExtendWith(SlactTestContainerExtension.class)
class ActorStoppingTest {

  private static class TestActor extends Actor<String> {

    @Override
    public void onMessage(@NotNull String message) {
      throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
    }
  }

  @Nested
  class ActorsCanBeStopped {

    @Test
    void whenUsingContainer(final @NotNull SlactTestContainer container) throws Throwable {

      final var onStopCalled = new AtomicBoolean();

      final var actorToBeStopped = container.spawn("actor-to-be-stopped", () -> new TestActor() {

        @Override
        public void onStop() {
          super.onStop();
          onStopCalled.set(true);
        }
      });

      final var eventualStopResult = container.stop(actorToBeStopped);

      await().atMost(DEFAULT_TIMEOUT).until(eventualStopResult::isDone);

      assertThat(eventualStopResult.get()).isSameAs(Done.instance());
      assertThat(onStopCalled).isTrue();
      assertThat(container.resolve(actorToBeStopped.path())).isEmpty();
    }

    @Test
    void whenFromInsideActor(final @NotNull SlactTestContainer container) throws Throwable {

      final var onStopCalled = new AtomicBoolean();
      final var eventualStopResult = new AtomicReference<Future<Done>>();

      final var actorToBeStopped = container.spawn("actor-to-be-stopped", () -> new TestActor() {

        @Override
        public void onStart() {
          super.onStart();
          eventualStopResult.set(context().stop(self()));
        }

        @Override
        public void onStop() {
          super.onStop();
          onStopCalled.set(true);
        }
      });

      await().atMost(DEFAULT_TIMEOUT)
          .until(() -> eventualStopResult.get() != null && eventualStopResult.get().isDone());

      assertThat(eventualStopResult.get().get()).isSameAs(Done.instance());
      assertThat(onStopCalled).isTrue();
      assertThat(container.resolve(actorToBeStopped.path())).isEmpty();
    }

    @Test
    void whenFromInsideAnotherActor(final @NotNull SlactTestContainer container) throws Throwable {

      final var onStopCalled = new AtomicBoolean();

      final var actorToBeStopped = container.spawn("actor-to-be-stopped",
          () -> new Actor<String>() {

            @Override
            public void onMessage(final @NotNull String message) {
              throw new UnsupportedOperationException();
            }

            @Override
            public void onStop() {
              super.onStop();
              onStopCalled.set(true);
            }
          });

      final var eventualStopResult = new AtomicReference<Future<Done>>();

      container.spawn("actor", () -> new TestActor() {

        @Override
        public void onStart() {
          super.onStart();
          eventualStopResult.set(context().stop(actorToBeStopped));
        }
      });

      await().atMost(DEFAULT_TIMEOUT)
          .until(() -> eventualStopResult.get() != null && eventualStopResult.get().isDone());

      assertThat(eventualStopResult.get().get()).isSameAs(Done.instance());
      assertThat(onStopCalled).isTrue();
      assertThat(container.resolve(actorToBeStopped.path())).isEmpty();
    }
  }
}
