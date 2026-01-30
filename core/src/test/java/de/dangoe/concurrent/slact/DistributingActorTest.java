package de.dangoe.concurrent.slact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.DistributingActor.Protocol.DistributionMethod;
import de.dangoe.concurrent.slact.DistributingActor.Protocol.Message;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Fail;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Given an routing actor")
public class DistributingActorTest {

  private final SlactContainer container = new SlactContainerBuilder().build();

  @Nested
  @DisplayName("When round robin method is used")
  class WhenRoundRobinMethodIsUsed {

    @Nested
    @DisplayName("When one child actor is used")
    class WhenOneChildActorIsUsed {

      @Test
      void shouldForwardTheWorkToTheChildActorAndReturnTheExpectedResult() {

        final var capturedMessage = new AtomicReference<String>();

        final var actor = container.spawn(
            () -> new DistributingActor<>(1, () -> new Actor<String>() {

              @Override
              public void onMessage(final @NotNull String message) {
                if (!capturedMessage.compareAndSet(null, message)) {
                  Fail.fail("Received a second message: %s".formatted(message));
                }
                capturedMessage.set(message);
              }
            }));

        container.send((Message) new Message.Initialize(DistributionMethod.ROUND_ROBIN)).to(actor);
        container.send((Message) new Message.Distribute<>("Hello world!")).to(actor);

        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(capturedMessage.get()).isEqualTo("Hello world!"));
      }
    }
  }
}
