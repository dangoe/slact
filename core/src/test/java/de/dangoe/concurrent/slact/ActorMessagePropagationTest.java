package de.dangoe.concurrent.slact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;


class ActorMessagePropagationTest {

  private record Pair<A, B>(A a, B b) {

  }

  private final Slact slact = Slact.createRuntime();

  @Test
  void messageCorrelationShouldWork() {

    final var messageIds = Collections.synchronizedSet(new HashSet<>());
    final var correlationMessageIds = Collections.synchronizedSet(new HashSet<>());

    final var otherActor = slact.register("other-actor", () -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        context().correlationMessageId().ifPresent(correlationMessageIds::add);
      }
    });

    final var actor = slact.register("actor", () -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        messageIds.add(context().messageId());
        send(message).to(otherActor);
      }
    });

    final var messages = IntStream.range(0, 1).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      slact.send(message).to(actor);
    }

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(correlationMessageIds).hasSize(messages.size())
            .containsExactlyElementsOf(messageIds));
  }

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
        send(message).to((ActorHandle<? extends String>) parent());
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
        forward(message).to(targetActor);
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

  @Test
  void actorResponseCanBeAwaited() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var actor = slact.register("actor", () -> new Actor<String>() {
      @Override
      protected void onMessage(final String message) {
        result.add(new Pair<>(context().sender().path(), message));
      }
    });

    // slact.ask("Hello world!").to("Hello world!");
  }
}
