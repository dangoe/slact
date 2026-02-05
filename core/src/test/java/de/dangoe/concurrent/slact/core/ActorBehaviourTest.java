package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import de.dangoe.concurrent.slact.testkit.patterns.actors.ForwardingActor;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
public class ActorBehaviourTest {

  @Nested
  class GivenAnActorWithDifferentBehaviours {

    @Nested
    class WhenTheBehaviourIsChangedForFirstSpecialMessageReceived {

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

        final var forwardingActor = container.spawn("resend-actor",
            () -> new ForwardingActor<>(actor));

        container.sendMultiple(List.of("initialize", "Hello world!")).to(forwardingActor);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
            () -> assertThat(result.get()).isEqualTo(
                new ReceivedMessage<>("Hello world!", ActorPath.root().append("resend-actor"))));
      }
    }

    @Nested
    class WhenTheBehaviourIsChangedMultipleTimesForTwoSpecialMessages {

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

        final var forwardingActor = container.spawn("resend-actor",
            () -> new ForwardingActor<>(actor));

        container.sendMultiple(List.of("Ready!", "Steady!", "Go!")).to(forwardingActor);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
            () -> assertThat(result.get()).isEqualTo(
                new ReceivedMessage<>("Go!", ActorPath.root().append("resend-actor"))));
      }
    }
  }
}
