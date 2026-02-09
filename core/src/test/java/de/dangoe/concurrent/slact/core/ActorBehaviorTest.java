package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
public class ActorBehaviorTest {

  @Nested
  class GivenAnActorWithDifferentBehaviours {

    @Nested
    class WhenTheBehaviorIsChangedForFirstSpecialMessageReceived {

      @Test
      void shouldChangeTheBehaviour(final @NotNull SlactTestContainer container) {

        final var result = new AtomicReference<ReceivedMessage<String>>();

        final var actor = container.spawn("actor", () -> new Actor<String>() {
          @Override
          public void onMessage(final @NotNull String message) {
            if ("initialize".equals(message)) {
              behaveAs(this::secondBehaviour);
            }
          }

          private void secondBehaviour(final String message) {
            result.set(new ReceivedMessage<>(message, sender().path()));
          }
        });

        final var resendingActor = container.spawn("resending-actor", () -> new Actor<String>() {
          @Override
          public void onMessage(final @NotNull String message) {
            send(message).to(actor);
          }
        });

        container.sendMultiple(List.of("initialize", "Hello world!")).to(resendingActor);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
            () -> assertThat(result.get()).isEqualTo(new ReceivedMessage<>("Hello world!",
                ActorPath.root().append("resending-actor"))));
      }
    }

    @Nested
    class WhenTheBehaviorIsChangedMultipleTimesForTwoSpecialMessages {

      @Test
      void shouldChangeTheBehaviourAccordingly(final @NotNull SlactTestContainer container) {

        final var result = new AtomicReference<ReceivedMessage<String>>();

        final var actor = container.spawn("actor", () -> new Actor<String>() {
          @Override
          public void onMessage(final @NotNull String message) {
            if ("Ready!".equals(message)) {
              behaveAs(this::readyBehaviour);
            }
          }

          private void readyBehaviour(final String message) {
            if ("Steady!".equals(message)) {
              behaveAs(this::startBehaviour);
            }
          }

          private void startBehaviour(final String message) {
            result.set(new ReceivedMessage<>(message, sender().path()));
          }
        });

        final var resendingActor = container.spawn("resending-actor", () -> new Actor<String>() {
          @Override
          public void onMessage(final @NotNull String message) {
            send(message).to(actor);
          }
        });

        container.sendMultiple(List.of("Ready!", "Steady!", "Go!")).to(resendingActor);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
            () -> assertThat(result.get()).isEqualTo(
                new ReceivedMessage<>("Go!", ActorPath.root().append("resending-actor"))));
      }
    }
  }
}
