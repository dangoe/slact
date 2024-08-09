package de.dangoe.concurrent.slact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActorExterminationTest {

  private static final class TestActor extends Actor<String> {

    @Override
    public void onMessage(String message) {
      throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
    }
  }

  private final SlactContainer container = new SlactContainerBuilder().build();

  @Nested
  class ActorsCanBeExterminated {

    @Test
    void whenUsingContainer() {

      final var actor = container.spawn("actor", TestActor::new);

      container.exterminate(actor);

      await().atMost(Duration.ofSeconds(5)).until(() -> container.resolve(actor.path()).isEmpty());
    }

    @Test
    void whenFromInsideActor() {

      final var actor = container.spawn("actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final String message) {
          context().exterminate(self());
        }
      });

      container.send("Exterminate!").to(actor);

      await().atMost(Duration.ofSeconds(5)).until(() -> container.resolve(actor.path()).isEmpty());
    }

    @Test
    void whenFromInsideAnotherActor() {

      final var actor = container.spawn("actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final String message) {
          throw new UnsupportedOperationException();
        }
      });

      final var otherActor = container.spawn("other-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final String message) {
          context().exterminate(actor);
        }
      });

      container.send("Exterminate!").to(otherActor);

      await().atMost(Duration.ofSeconds(5)).until(() -> container.resolve(actor.path()).isEmpty());
      assertThat(container.resolve(otherActor.path())).isNotEmpty();
    }
  }
}
