package de.dangoe.concurrent.slact;

import static de.dangoe.concurrent.slact.testhelper.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testhelper.MessageCapturingActor;
import de.dangoe.concurrent.slact.testhelper.MessageCapturingActor.MessageWithSender;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ActorHandleTest {

  private final @NotNull SlactContainer container = new SlactContainerBuilder().build();

  @Nested
  @DisplayName("Given an actor spawned from the root actor and a child actor spawned from the first actor")
  class GivenAnActorSpawnedFromTheRootActorAndChildActorSpawnedFromTheFirstActor {

    private @NotNull MessageCapturingActor<String> actor;
    private @NotNull ActorHandle<String> actorHandle;

    private @NotNull MessageCapturingActor<String> childActor;
    private @NotNull ActorHandle<String> childActorHandle;

    @BeforeEach
    void setUp() {

      actor = new MessageCapturingActor<>();
      actorHandle = container.spawn("actor", () -> actor);

      childActor = new MessageCapturingActor<>();
      childActorHandle = actorHandle.spawn("child", () -> childActor);
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
      void thenTheMessageShouldHaveBeenReceivedByTheActor() {

        final var sendingActorHandle = container.spawn("sending-actor", () -> new Actor<String>() {

          @Override
          public void onMessage(@NotNull String message) {
            // Not implemented
          }

          @Override
          void onStart() {
            actorHandle.send("Hello world!");
          }
        });

        await().atMost(DEFAULT_TIMEOUT).untilAsserted(
            () -> assertThat(actor.receivedMessages()).containsOnly(
                new MessageWithSender<>("Hello world!", sendingActorHandle)));
      }

      @Test
      @DisplayName("Then the message should have been received by the child actor.")
      void thenTheMessageShouldHaveBeenReceivedByTheChildActor() {

        final var sendingActorHandle = container.spawn("sending-actor", () -> new Actor<String>() {

          @Override
          public void onMessage(@NotNull String message) {
            // Not implemented
          }

          @Override
          void onStart() {
            childActorHandle.send("Hello world!");
          }
        });

        await().atMost(DEFAULT_TIMEOUT).untilAsserted(
            () -> assertThat(childActor.receivedMessages()).containsOnly(
                new MessageWithSender<>("Hello world!", sendingActorHandle)));
      }
    }
  }
}
