package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
class ActorMessagePropagationTest {

  @Test
  @SuppressWarnings("OptionalGetWithoutIsPresent")
  void messageCanBeSendToActorsWhenAnActorPathIsUsed(final @NotNull SlactTestContainer container) {

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
  void messagesCanBeSent(final @NotNull SlactTestContainer container) {

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

  @Test
  void repliesCanBeSent(final @NotNull SlactTestContainer container) {

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

    final var messages = IntStream.range(0, 1).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      container.send(message).to(actor);
    }

    await().atMost(Duration.ofSeconds(5)).untilAsserted(
        () -> assertThat(result).hasSize(messages.size()).containsExactlyElementsOf(
            messages.stream().map(message -> new ReceivedMessage<>("_%s_".formatted(message),
                ActorPath.root().append("other-actor"))).toList()));
  }

  @Test
  void messagesCanBeResent(final @NotNull SlactTestContainer container) {

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

  @Test
  void messageCanBeSendToParentActor(final @NotNull SlactTestContainer container) {

    final var result = new CopyOnWriteArrayList<ReceivedMessage<String>>();

    final var parentActor = container.spawn("parent-actor", () -> new Actor<String>() {
      @Override
      public void onMessage(final @NotNull String message) {
        result.add(new ReceivedMessage<>(message, context().sender().path()));
      }
    });

    final var childActor = parentActor.spawn("child-actor", () -> new Actor<String>() {
      @Override
      public void onMessage(final @NotNull String message) {
        send(message).to(parent());
      }
    });

    final var messages = IntStream.range(0, 1).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      container.send(message).to(childActor);
    }

    await().atMost(Duration.ofSeconds(5)).untilAsserted(
        () -> assertThat(result).containsExactlyElementsOf(
            messages.stream().map(msg -> new ReceivedMessage<>(msg, childActor.path())).toList()));
  }

  @Test
  void messagesCanBeForwarded(final @NotNull SlactTestContainer container) {

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
