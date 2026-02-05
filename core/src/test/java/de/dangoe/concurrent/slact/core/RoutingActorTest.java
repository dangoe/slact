package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor;
import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor.RoutingRequest;
import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor.SimpleRoutingRequest;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Fail;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Given an routing actor")
@ExtendWith(SlactTestContainerExtension.class)
public class RoutingActorTest {

  @Nested
  @DisplayName("When round robin method is used")
  class WhenRoundRobinMethodIsUsed {

    @Nested
    @DisplayName("When one child actor is used")
    class WhenOneChildActorIsUsed {

      @Test
      void shouldForwardTheWorkToTheChildActorAndReturnTheExpectedResult(final @NotNull SlactTestContainer container) {

        final var capturedMessage = new AtomicReference<String>();

        final var actor = container.spawn(
            RoutingActor.roundRobinWorker(1, () -> new Actor<String>() {

              @Override
              public void onMessage(final @NotNull String message) {
                if (!capturedMessage.compareAndSet(null, message)) {
                  Fail.fail("Received a second message: %s".formatted(message));
                }
                capturedMessage.set(message);
              }
            }));

        container.send((RoutingRequest<String>) new SimpleRoutingRequest<>("Hello world!"))
            .to(actor);

        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(capturedMessage.get()).isEqualTo("Hello world!"));
      }
    }
  }
}
