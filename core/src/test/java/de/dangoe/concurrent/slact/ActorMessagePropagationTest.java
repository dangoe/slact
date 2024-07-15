package de.dangoe.concurrent.slact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.api.Actor;
import de.dangoe.concurrent.slact.api.ActorHandle;
import de.dangoe.concurrent.slact.api.ActorPath;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ActorMessagePropagationTest {

  private record Pair<A, B>(A a, B b) {

  }

  private final Slact slact = Slact.createRuntime();

  @Test
  void messagesCanBeSent() {

    final var result = new CopyOnWriteArrayList<String>();

    final var actor = slact.spawn(() -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        result.add(message);
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
  void eventualMessagesCanBePiped() {

    try (final var executor = Executors.newFixedThreadPool(12)) {

      final var result = new CopyOnWriteArrayList<String>();

      final var terminalActor = slact.spawn(() -> new Actor<String>() {
        @Override
        protected void onMessageInternal(final String message) {
          result.add(message);
        }
      });

      final var actor = slact.spawn(() -> new Actor<String>() {
        @Override
        protected void onMessageInternal(final String message) {

          final var future = new CompletableFuture<String>();

          pipe(future).to(terminalActor);

          executor.execute(() -> {
            try {
              Thread.sleep(new Random().nextInt(0, 250));
              future.complete(message);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          });
        }
      });

      final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

      for (final var message : messages) {
        slact.send(message).to(actor);
      }

      await().atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(result).containsExactlyInAnyOrderElementsOf(messages));
    }
  }

  @Test
  void messageCorrelationShouldWork() {

    final var messageIds = Collections.synchronizedSet(new HashSet<>());
    final var correlationMessageIds = Collections.synchronizedSet(new HashSet<>());

    final var otherActor = slact.spawn("other-actor", () -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        context().correlationMessageId().ifPresent(correlationMessageIds::add);
      }
    });

    final var actor = slact.spawn("actor", () -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        messageIds.add(context().messageId());
        send(message).to(otherActor);
      }
    });

    final var messages = IntStream.range(0, 1).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      slact.send(message).to(actor);
    }

    await().atMost(Duration.ofSeconds(5)).untilAsserted(
        () -> assertThat(correlationMessageIds).hasSize(messages.size())
            .containsExactlyElementsOf(messageIds));
  }

  @Test
  void messagesCanBeResent() {

    final var result = new CopyOnWriteArrayList<String>();

    final var terminalActor = slact.spawn(() -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        result.add(message);
      }
    });

    final var actor = slact.spawn(() -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
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

    final var parentActor = slact.spawn("parent-actor", () -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        result.add(new Pair<>(context().sender().path(), message));
      }
    });

    final var childActor = parentActor.spawn("child-actor", () -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        send(message).to((ActorHandle<? extends String>) parent());
      }
    });

    final var messages = IntStream.range(0, 1).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      slact.send(message).to(childActor);
    }

    await().atMost(Duration.ofSeconds(5)).untilAsserted(
        () -> assertThat(result).containsExactlyElementsOf(
            messages.stream().map(msg -> new Pair<>(childActor.path(), msg)).toList()));
  }

  @Test
  void messagesCanBeForwarded() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var targetActor = slact.spawn("target-actor", () -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        result.add(new Pair<>(context().sender().path(), message));
      }
    });

    final var mediatorActor = slact.spawn("mediator-actor", () -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        forward(message).to(targetActor);
      }
    });

    final var originActor = slact.spawn("origin-actor", () -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
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
  void actorResponseCanBeRequested() throws Exception {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var actor = slact.spawn("actor", () -> new Actor<String>() {
      @Override
      protected void onMessageInternal(final String message) {
        respond("Hi there!");
      }
    });

    final Future<String> eventualResponse = slact.<String, String>requestResponseTo("Hello world!")
        .from(actor);

    await().atMost(Duration.ofSeconds(5)).until(eventualResponse::isDone);

    assertThat(eventualResponse.get()).isEqualTo("Hi there!");
  }
}
