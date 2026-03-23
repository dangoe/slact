package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.Constants;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
@DisplayName("Actor behavior")
public class ActorBehaviorTest {

  @Nested
  @DisplayName("given an actor with different behaviours")
  class GivenAnActorWithDifferentBehaviours {

    @Nested
    @DisplayName("when the behavior is changed for the first special message received")
    class WhenTheBehaviorIsChangedForFirstSpecialMessageReceived {

      @Test
      @DisplayName("should change the behaviour")
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

        await().atMost(Constants.DEFAULT_TIMEOUT).untilAsserted(
            () -> assertThat(result.get()).isEqualTo(
                new ReceivedMessage<>("Hello world!", ActorPath.root().append("resending-actor"))));
      }
    }

    @Nested
    @DisplayName("when the behavior is changed multiple times for two special messages")
    class WhenTheBehaviorIsChangedMultipleTimesForTwoSpecialMessages {

      @Test
      @DisplayName("should change the behaviour accordingly")
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

        await().atMost(Constants.DEFAULT_TIMEOUT).untilAsserted(
            () -> assertThat(result.get()).isEqualTo(
                new ReceivedMessage<>("Go!", ActorPath.root().append("resending-actor"))));
      }
    }

    @Nested
    @DisplayName("when the behavior is switched back to default after a prior change")
    class WhenTheBehaviorIsSwitchedBackToDefault {

      @Test
      @DisplayName("should resume the original default behaviour")
      void shouldResumeTheOriginalDefaultBehaviour(final @NotNull SlactTestContainer container) {

        final var defaultHandledCount = new AtomicInteger(0);
        final var alternateHandledCount = new AtomicInteger(0);

        final var actor = container.spawn("actor", () -> new Actor<String>() {
          @Override
          public void onMessage(final @NotNull String message) {
            if ("switch".equals(message)) {
              behaveAs(this::alternateBehaviour);
            } else {
              defaultHandledCount.incrementAndGet();
            }
          }

          private void alternateBehaviour(final String message) {
            if ("reset".equals(message)) {
              behaveAsDefault();
            } else {
              alternateHandledCount.incrementAndGet();
            }
          }
        });

        container.sendMultiple(List.of("default-1", "switch", "alternate-1", "reset", "default-2"))
            .to(actor);

        await().atMost(Constants.DEFAULT_TIMEOUT).untilAsserted(() -> {
          assertThat(defaultHandledCount.get()).isEqualTo(2);
          assertThat(alternateHandledCount.get()).isEqualTo(1);
        });
      }
    }
  }
}
