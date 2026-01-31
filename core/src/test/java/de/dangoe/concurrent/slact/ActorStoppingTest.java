package de.dangoe.concurrent.slact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActorStoppingTest {

  private static final class TestActor extends Actor<String> {

    @Override
    public void onMessage(@NotNull String message) {
      throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
    }
  }

  private final SlactContainer container = new SlactContainerBuilder().build();

  @Nested
  class ActorsCanBeStopped {

    @Test
    void whenUsingContainer() {

      final var actor = container.spawn("actor", TestActor::new);

      container.stop(actor);

      await().atMost(Duration.ofSeconds(5)).until(() -> container.resolve(actor.path()).isEmpty());
    }

    @Test
    void whenFromInsideActor() {

      final var actor = container.spawn("actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          context().stop(self());
        }
      });

      container.send("Stop!").to(actor);

      await().atMost(Duration.ofSeconds(5)).until(() -> container.resolve(actor.path()).isEmpty());
    }

    @Test
    void whenFromInsideAnotherActor() {

      final var actor = container.spawn("actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          throw new UnsupportedOperationException();
        }
      });

      final var otherActor = container.spawn("other-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          context().stop(actor);
        }
      });

      container.send("Stop!").to(otherActor);

      await().atMost(Duration.ofSeconds(5)).until(() -> container.resolve(actor.path()).isEmpty());
      assertThat(container.resolve(otherActor.path())).isNotEmpty();
    }
  }
}
