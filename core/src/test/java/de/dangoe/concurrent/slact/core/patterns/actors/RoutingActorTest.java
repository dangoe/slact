package de.dangoe.concurrent.slact.core.patterns.actors;

import static de.dangoe.concurrent.slact.core.testhelper.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor.RoutingRequest;
import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor.SimpleRoutingRequest;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
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
  @DisplayName("When custom routing strategy is used")
  class WhenCustomRoutingStrategyIsUsed {

    private record BlueGreenRoutingRequest(boolean isGreen, Integer message) implements
        RoutingRequest<Integer> {

    }

    @Test
    void shouldRouteTheMessagesAsExpected(final @NotNull SlactTestContainer container) {

      final var greenCount = new AtomicInteger();
      final var blueCount = new AtomicInteger();

      final var greenActor = container.spawn(() -> new Actor<Integer>() {

        @Override
        public void onMessage(final @NotNull Integer message) {
          greenCount.getAndUpdate(value -> value + message);
        }
      });

      final var blueActor = container.spawn(() -> new Actor<Integer>() {

        @Override
        public void onMessage(final @NotNull Integer message) {
          blueCount.getAndUpdate(value -> value + message);
        }
      });

      final var actor = container.spawn(RoutingActor.<BlueGreenRoutingRequest, Integer>custom(
          context -> request -> request.isGreen() ? greenActor : blueActor));

      container.awaitReady(greenActor.path(), blueActor.path(), actor.path());

      container.send(new BlueGreenRoutingRequest(true, 1)).to(actor);
      container.send(new BlueGreenRoutingRequest(true, 2)).to(actor);
      container.send(new BlueGreenRoutingRequest(false, 4)).to(actor);

      await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {
        assertThat(greenCount.get()).isEqualTo(3);
        assertThat(blueCount.get()).isEqualTo(4);
      });
    }
  }

  @Nested
  @DisplayName("When round robin method is used")
  class WhenRoundRobinMethodIsUsed {

    @Nested
    @DisplayName("When one worker actor is used")
    class WhenOneWorkerActorIsUsed {

      @Test
      @DisplayName("Should route the work as expected")
      void shouldRouteTheWorkAsExpected(final @NotNull SlactTestContainer container) {

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

        await().atMost(DEFAULT_TIMEOUT)
            .untilAsserted(() -> assertThat(capturedMessage.get()).isEqualTo("Hello world!"));
      }
    }

    @Test
    @DisplayName("When multiple worker actors are used")
    void whenMultipleWorkerActorsAreUsed(final @NotNull SlactTestContainer container) {

      final var actorCounter = new AtomicInteger(1);

      final var computedResult = new ConcurrentHashMap<Integer, Integer>();

      final var actor = container.spawn(
          RoutingActor.roundRobinWorker(3, () -> new Actor<Integer>() {

            private final int id = actorCounter.getAndIncrement();

            @Override
            public void onMessage(final @NotNull Integer message) {
              computedResult.compute(id,
                  (key, value) -> id * (Optional.ofNullable(value).orElse(0) + message));
            }
          }));

      container.awaitReady(actor.path());

      container.sendMultiple(
              IntStream.range(0, 6).boxed().map(it -> new SimpleRoutingRequest<>(2)).toList())
          .to(actor);

      await().atMost(DEFAULT_TIMEOUT)
          .untilAsserted(() -> assertThat(computedResult).containsExactlyInAnyOrderEntriesOf(
              Map.of(1, 4, 2, 12, 3, 24)
          ));
    }
  }
}
