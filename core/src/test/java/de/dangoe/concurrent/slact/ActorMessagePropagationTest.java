package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorContext.SendableMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


class ActorMessagePropagationTest {

  private record Pair<A, B>(A a, B b) {

  }

  private final Slact slact = Slact.createRuntime();

  @Test
  void messagesCanBeResend() {

    final var result = new CopyOnWriteArrayList<String>();

    final var terminalActor = slact.register(() -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        result.add(message);
      }
    });

    final var actor = slact.register(() -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        send(message).to(terminalActor);
      }
    });

    final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      slact.send(message).to(actor);
    }

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(result).containsExactlyElementsOf(messages));
  }

  @Test
  void messageCanBeSendToParentActor() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var parentActor = slact.register("parent-actor", () -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        result.add(new Pair<>(context().sender().path(), message));
      }
    });

    final var childActor = parentActor.register("child-actor", () -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        SendableMessage<String> send = send(message);
        send.to((ActorHandle<? extends String>) parent());
      }
    });

    final var messages = IntStream.range(0, 1).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      slact.send(message).to(childActor);
    }

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(result).containsExactlyElementsOf(
            messages.stream().map(msg -> new Pair<>(childActor.path(), msg))
                .toList()));
  }

  @Test
  void messagesCanBeForwarded() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var targetActor = slact.register("target-actor", () -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        result.add(new Pair<>(context().sender().path(), message));
      }
    });

    final var mediatorActor = slact.register("mediator-actor", () -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        targetActor.forward(message, context());
      }
    });

    final var originActor = slact.register("origin-actor", () -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        send(message).to(mediatorActor);
      }
    });

    final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      slact.send(message).to(originActor);
    }

    await().atMost(Duration.ofSeconds(5)).untilAsserted(
        () -> assertThat(result).containsExactlyElementsOf(
            messages.stream().map(msg -> new Pair<>(ActorPath.root().append("origin-actor"), msg))
                .toList()));
  }
}
