package de.dangoe.concurrent.slact.core;

import static de.dangoe.concurrent.slact.core.testhelper.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import de.dangoe.concurrent.slact.testkit.patterns.actors.CapturingActor;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
public class ActorHandleTest {

  @Nested
  @DisplayName("Given an actor spawned from the root actor and a child actor spawned from the first actor")
  class GivenAnActorSpawnedFromTheRootActorAndChildActorSpawnedFromTheFirstActor {

    private @NotNull CapturingActor<String> actor;
    private @NotNull ActorHandle<String> actorHandle;

    private @NotNull CapturingActor<String> childActor;
    private @NotNull ActorHandle<String> childActorHandle;

    @BeforeEach
    void setUp(final @NotNull SlactTestContainer container) {

      actor = new CapturingActor<>() {

        @Override
        public void onStart() {
          super.onStart();
          childActor = new CapturingActor<>();
          childActorHandle = context().spawn("child", () -> childActor);
        }
      };

      actorHandle = container.spawn("actor", () -> actor);

      await().atMost(DEFAULT_TIMEOUT).untilAsserted(
          () -> {
            assertThat(childActorHandle).isNotNull();
            assertThat(container.isReady(actorHandle.path())).isTrue();
            assertThat(container.isReady(childActorHandle.path())).isTrue();
          });
    }

    @Nested
    @DisplayName("When actor path is requested actors")
    class WhenActorPathIsRequestedActors {

      @Test
      @DisplayName("Then should return the expected path for the actor.")
      void thenShouldReturnTheExpectedPathForTheActor() {
        assertThat(actorHandle.path()).isEqualTo(ActorPath.root().append("actor"));
      }

      @Test
      @DisplayName("Then should return the expected path for the child actor.")
      void thenShouldReturnTheExpectedPathForTheChildActor() {
        assertThat(childActorHandle.path()).isEqualTo(
            ActorPath.root().append("actor").append("child"));
      }
    }

    @Nested
    @DisplayName("When message is sent from another actor")
    class WhenMessageIsSentFromAnotherActor {

      @Test
      @DisplayName("Then the message should have been received by the actor.")
      void thenTheMessageShouldHaveBeenReceivedByTheActor(
          final @NotNull SlactTestContainer container) {

        final var sendingActorHandle = container.spawn("sending-actor", () -> new Actor<String>() {

          @Override
          public void onStart() {
            actorHandle.send("Hello world!");
          }

          @Override
          public void onMessage(@NotNull String message) {
            // Not implemented
          }
        });

        await().atMost(DEFAULT_TIMEOUT).untilAsserted(
            () -> assertThat(actor.receivedMessages()).containsOnly(
                new ReceivedMessage<>("Hello world!", sendingActorHandle)));
      }

      @Test
      @DisplayName("Then the message should have been received by the child actor.")
      void thenTheMessageShouldHaveBeenReceivedByTheChildActor(
          final @NotNull SlactTestContainer container) {

        final var sendingActorHandle = container.spawn("sending-actor", () -> new Actor<String>() {

          @Override
          public void onStart() {
            childActorHandle.send("Hello world!");
          }

          @Override
          public void onMessage(@NotNull String message) {
            // Not implemented
          }
        });

        await().atMost(DEFAULT_TIMEOUT).untilAsserted(
            () -> assertThat(childActor.receivedMessages()).containsOnly(
                new ReceivedMessage<>("Hello world!", sendingActorHandle)));
      }
    }
  }
}
