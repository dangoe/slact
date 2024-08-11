package de.dangoe.concurrent.slact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActorMessagePropagationTest {

  private record Pair<A, B>(A a, B b) {

  }

  private final SlactContainer container = new SlactContainerBuilder().build();

  @Test
  void anWordCountActorCanBeBuilt() {

    final var wordCount = new AtomicInteger(0);

    final var actor = container.spawn(() -> new Actor<String>() {
      @Override
      public void onMessage(final @NotNull String message) {
        wordCount.addAndGet(message.split(" ").length);
      }
    });

    try {
      final var lines = Files.readAllLines(
          Paths.get(Objects.requireNonNull(getClass().getResource("lorem-ipsum.txt")).toURI()));

      for (final var line : lines) {
        container.send(line).to(actor);
      }
    } catch (IOException | URISyntaxException e) {
      fail(e);
    }

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(wordCount.get()).isEqualTo(9895));
  }

  @Test
  @SuppressWarnings("OptionalGetWithoutIsPresent")
  void messageCanBeSendToActorsWhenAnActorPathIsUsed() {

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
  void messagesCanBeSent() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var actor = container.spawn(() -> new Actor<String>() {
      @Override
      public void onMessage(final @NotNull String message) {
        result.add(new Pair<>(sender().path(), message));
      }
    });

    final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      container.send(message).to(actor);
    }

    await().atMost(Duration.ofSeconds(5)).untilAsserted(
        () -> assertThat(result).containsExactlyElementsOf(
            messages.stream().map(message -> new Pair<>(ActorPath.root(), message)).toList()));
  }

  @Test
  void repliesCanBeSent() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

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
          result.add(new Pair<>(sender().path(), message));
        }
      }
    });

    final var messages = IntStream.range(0, 1).boxed().map("m_%d"::formatted).toList();

    for (final var message : messages) {
      container.send(message).to(actor);
    }

    await().atMost(Duration.ofSeconds(5)).untilAsserted(
        () -> assertThat(result).hasSize(messages.size()).containsExactlyElementsOf(
            messages.stream().map(message -> new Pair<>(ActorPath.root().append("other-actor"),
                "_%s_".formatted(message))).toList()));
  }

  @Test
  void eventualMessagesCanBePiped() {

    try (final var executor = Executors.newFixedThreadPool(12)) {

      final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

      final var terminalActor = container.spawn("terminal-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          result.add(new Pair<>(sender().path(), message));
        }
      });

      final var actor = container.spawn("actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {

          final var future = new CompletableFuture<String>();

          pipeFuture(future).to(terminalActor);

          executor.execute(() -> {
            try {
              Thread.sleep(new Random().nextInt(0, 150));
              future.complete(message);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          });
        }
      });

      final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

      for (final var message : messages) {
        container.send(message).to(actor);
      }

      await().atMost(Duration.ofSeconds(1000)).untilAsserted(
          () -> assertThat(result).containsExactlyInAnyOrderElementsOf(messages.stream()
              .map(message -> new Pair<>(ActorPath.root().append("actor"), message)).toList()));
    }
  }

  @Test
  void messagesCanBeResent() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var terminalActor = container.spawn("terminal-actor", () -> new Actor<String>() {
      @Override
      public void onMessage(final @NotNull String message) {
        result.add(new Pair<>(sender().path(), message));
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
        () -> assertThat(result).containsExactlyElementsOf(
            messages.stream().map(message -> new Pair<>(ActorPath.root().append("actor"), message))
                .toList()));
  }

  @Test
  void messageCanBeSendToParentActor() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var parentActor = container.spawn("parent-actor", () -> new Actor<String>() {
      @Override
      public void onMessage(final @NotNull String message) {
        result.add(new Pair<>(context().sender().path(), message));
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
            messages.stream().map(msg -> new Pair<>(childActor.path(), msg)).toList()));
  }

  @Test
  void messagesCanBeForwarded() {

    final var result = new CopyOnWriteArrayList<Pair<ActorPath, String>>();

    final var targetActor = container.spawn("target-actor", () -> new Actor<String>() {
      @Override
      public void onMessage(final @NotNull String message) {
        result.add(new Pair<>(context().sender().path(), message));
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
        () -> assertThat(result).containsExactlyElementsOf(
            messages.stream().map(msg -> new Pair<>(ActorPath.root().append("origin-actor"), msg))
                .toList()));
  }

  @Nested
  class RequestResponseTo {

    @Nested
    class CanBeUsedToAskAnActorForAnAsynchronousResponse {

      @Nested
      class WhenRootIsOrigin {

        @Test
        void whenReplyIsUsed() throws Exception {

          final var actor = container.spawn("actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {
              respondWith("Hi there!");
            }
          });

          final var eventualResponse = container.<String, String>requestResponseTo("Hello world!")
              .from(actor);

          await().atMost(Duration.ofSeconds(5)).until(eventualResponse::isDone);

          assertThat(eventualResponse.get()).isEqualTo("Hi there!");
        }

        @Test
        void whenSendToSenderIsUsed() throws Exception {

          final var actor = container.spawn("actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {
              send("Hi there!").to(sender());
            }
          });

          final var eventualResponse = container.<String, String>requestResponseTo("Hello world!")
              .from(actor);

          await().atMost(Duration.ofSeconds(5)).until(eventualResponse::isDone);

          assertThat(eventualResponse.get()).isEqualTo("Hi there!");
        }
      }

      @Nested
      class WhenAnotherActorIsOrigin {

        @Test
        void whenRespondWithIsUsed() {

          final AtomicReference<String> result = new AtomicReference<>();

          final var actor = container.spawn("actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {
              respondWith("_%s_".formatted(message));
            }
          });

          final var otherActor = container.spawn("other-actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {
              if (!message.startsWith("_")) {
                final var eventualResult = this.<String, String>requestResponseTo(message)
                    .from(actor);
                pipeFuture(eventualResult).to(self());
              } else {
                result.set(message);
              }
            }
          });

          container.send("test").to(otherActor);

          await().atMost(Duration.ofSeconds(5)).until(() -> result.get() != null);

          assertThat(result.get()).isEqualTo("_test_");
        }

        @Test
        void whenSendToSenderIsUsed() throws Exception {

          final AtomicReference<String> result = new AtomicReference<>();

          final var actor = container.spawn("actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {
              send("_%s_".formatted(message)).to(sender());
            }
          });

          final var otherActor = container.spawn("other-actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {
              if (!message.startsWith("_")) {
                pipeFuture(this.<String, String>requestResponseTo(message).from(actor)).to(self());
              } else {
                result.set(message);
              }
            }
          });

          container.send("test").to(otherActor);

          await().atMost(Duration.ofSeconds(5)).until(() -> result.get() != null);

          assertThat(result.get()).isEqualTo("_test_");
        }
      }
    }
  }
}
