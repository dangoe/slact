package de.dangoe.concurrent.slact.core;

import static de.dangoe.concurrent.slact.core.testhelper.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
@DisplayName("Actor Message Propagation")
class ActorMessagePropagationTest {

  @Nested
  @DisplayName("given an actor handle obtained directly from spawn")
  class GivenAnActorHandleFromSpawn {

    @Test
    @DisplayName("when 100 messages are sent, then all are received in order")
    void whenMessagesSent_thenAllReceivedInOrder(final @NotNull SlactTestContainer container) {

      final var result = new CopyOnWriteArrayList<ReceivedMessage<String>>();

      final var actor = container.spawn(() -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          result.add(new ReceivedMessage<>(message, sender().path()));
        }
      });

      final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

      for (final var message : messages) {
        container.send(message).to(actor);
      }

      await().atMost(Duration.ofSeconds(5)).untilAsserted(
          () -> assertThat(result).containsExactlyElementsOf(
              messages.stream().map(message -> new ReceivedMessage<>(message, ActorPath.root()))
                  .toList()));
    }
  }

  @Nested
  @DisplayName("given an actor handle resolved by path")
  class GivenAnActorHandleResolvedByPath {

    @Test
    @DisplayName("when the path exists, then the handle is present and messages are received in order")
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void whenPathExists_thenHandlePresentAndMessagesReceived(
        final @NotNull SlactTestContainer container) {

      final var result = new CopyOnWriteArrayList<String>();

      final var actor = container.spawn(() -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          result.add(message);
        }
      });

      final var actorHandle = container.<String>resolve(actor.path()).get();
      final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

      for (final var message : messages) {
        container.send(message).to(actorHandle);
      }

      await().atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(result).containsExactlyElementsOf(messages));
    }

    @Test
    @DisplayName("when the path does not exist, then returns an empty Optional")
    void whenPathDoesNotExist_thenReturnsEmpty(final @NotNull SlactTestContainer container) {

      final var nonExistentPath = ActorPath.root().append("non-existent");

      final var result = container.<String>resolve(nonExistentPath);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("given two actors where one replies to the other")
  class GivenTwoActorsWhereOneReplies {

    @Test
    @DisplayName("when 100 messages are sent, then all replies are received with the correct sender path")
    void whenMessagesSent_thenRepliesReceivedWithCorrectSenderPath(
        final @NotNull SlactTestContainer container) {

      final var result = new CopyOnWriteArrayList<ReceivedMessage<String>>();

      final var otherActor = container.spawn("other-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          respondWith("_%s_".formatted(message));
        }
      });

      final var actor = container.spawn("actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          if (!message.startsWith("_")) {
            send(message).to(otherActor);
          } else {
            result.add(new ReceivedMessage<>(message, sender().path()));
          }
        }
      });

      final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

      for (final var message : messages) {
        container.send(message).to(actor);
      }

      await().atMost(Duration.ofSeconds(5)).untilAsserted(
          () -> assertThat(result).hasSize(messages.size()).containsExactlyElementsOf(
              messages.stream().map(message -> new ReceivedMessage<>("_%s_".formatted(message),
                  ActorPath.root().append("other-actor"))).toList()));
    }
  }

  @Nested
  @DisplayName("given an actor that re-sends messages to another actor")
  class GivenAnActorThatResendsMessages {

    @Test
    @DisplayName("when 100 messages are sent, then all are received at the terminal actor with the resending actor's path as sender")
    void whenMessagesSent_thenReceivedAtTerminalWithResendingActorAsSender(
        final @NotNull SlactTestContainer container) {

      final var result = new CopyOnWriteArrayList<ReceivedMessage<String>>();

      final var terminalActor = container.spawn("terminal-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          result.add(new ReceivedMessage<>(message, sender().path()));
        }
      });

      final var actor = container.spawn("actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          send(message).to(terminalActor);
        }
      });

      final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

      for (final var message : messages) {
        container.send(message).to(actor);
      }

      await().atMost(Duration.ofSeconds(5)).untilAsserted(
          () -> assertThat(result).containsExactlyElementsOf(messages.stream()
              .map(message -> new ReceivedMessage<>(message, ActorPath.root().append("actor")))
              .toList()));
    }
  }

  @Nested
  @DisplayName("given an actor that forwards messages to another actor")
  class GivenAnActorThatForwardsMessages {

    @Test
    @DisplayName("when 100 messages are sent, then the target actor receives them with the original sender's path preserved")
    void whenMessagesSent_thenTargetReceivesWithOriginalSenderPath(
        final @NotNull SlactTestContainer container) {

      final var result = new CopyOnWriteArrayList<ReceivedMessage<String>>();

      final var targetActor = container.spawn("target-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          result.add(new ReceivedMessage<>(message, context().sender().path()));
        }
      });

      final var mediatorActor = container.spawn("mediator-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          forward(message).to(targetActor);
        }
      });

      final var originActor = container.spawn("origin-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          send(message).to(mediatorActor);
        }
      });

      final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

      for (final var message : messages) {
        container.send(message).to(originActor);
      }

      await().atMost(Duration.ofSeconds(5)).untilAsserted(
          () -> assertThat(result).containsExactlyElementsOf(messages.stream()
              .map(msg -> new ReceivedMessage<>(msg, ActorPath.root().append("origin-actor")))
              .toList()));
    }
  }

  @Nested
  @DisplayName("given a child actor that sends messages to its parent")
  class GivenAChildActorThatSendsToParent {

    @Test
    @DisplayName("when 100 messages are sent to the child, then the parent receives all with the child's path as sender")
    void whenMessagesSentToChild_thenParentReceivesWithChildAsSender(
        final @NotNull SlactTestContainer container) {

      final var result = new CopyOnWriteArrayList<ReceivedMessage<String>>();

      final var parentActor = container.spawn("parent-actor", () -> new Actor<String>() {

        @Override
        public void onStart() {
          super.onStart();
          context().spawn("child-actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {
              send(message).to(parent());
            }
          });
        }

        @Override
        public void onMessage(final @NotNull String message) {
          result.add(new ReceivedMessage<>(message, context().sender().path()));
        }
      });

      final var childActorPath = parentActor.path().append("child-actor");

      await().atMost(DEFAULT_TIMEOUT).until(() -> container.resolve(childActorPath).isPresent());

      //noinspection OptionalGetWithoutIsPresent
      final var childActor = container.<String>resolve(childActorPath).get();

      final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

      for (final var message : messages) {
        container.send(message).to(childActor);
      }

      // Then
      await().atMost(Duration.ofSeconds(5)).untilAsserted(
          () -> assertThat(result).containsExactlyElementsOf(
              messages.stream().map(msg -> new ReceivedMessage<>(msg, childActor.path()))
                  .toList()));
    }
  }
}
