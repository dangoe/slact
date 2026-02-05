package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testsupport.ReceivedMessage;
import de.dangoe.concurrent.slact.testsupport.actor.ResendActor;
import de.dangoe.concurrent.slact.testsupport.SlactTestContainer;
import de.dangoe.concurrent.slact.testsupport.SlactTestContainerExtension;
import java.time.Duration;
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

        final var resendActor = container.spawn("resend-actor", () -> new ResendActor<>(actor));

        container.send("initialize").to(resendActor);
        container.send("Hello world!").to(resendActor);

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

        final var resendActor = container.spawn("resend-actor", () -> new ResendActor<>(actor));

        container.send("Ready!").to(resendActor);
        container.send("Steady!").to(resendActor);
        container.send("Go!").to(resendActor);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
            () -> assertThat(result.get()).isEqualTo(
                new ReceivedMessage<>("Go!", ActorPath.root().append("resend-actor"))));
      }
    }
  }
}
