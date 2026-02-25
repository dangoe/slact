package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
@DisplayName("Actor async response")
public class ActorAsyncResponseTest {

  @Nested
  @DisplayName("given an actor that works with results provided asynchronously")
  class GivenAnActorThatWorksWithResultsProvidedAsynchronously {

    @Test
    @DisplayName("should process piped results correctly when futures complete asynchronously")
    void shouldProcessPipedResultsCorrectly(final @NotNull SlactTestContainer container) {

      try (final var executor = Executors.newFixedThreadPool(12)) {

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

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
            () -> assertThat(result).containsExactlyInAnyOrderElementsOf(messages.stream()
                .map(message -> new ReceivedMessage<>(message, ActorPath.root().append("actor")))
                .toList()));
      }
    }

    @Test
    @DisplayName("should not deliver a message to the target actor when the piped future fails exceptionally")
    void shouldNotDeliverMessageWhenPipedFutureFailsExceptionally(
        final @NotNull SlactTestContainer container) {

      final var receivedMessages = new CopyOnWriteArrayList<String>();

      final var terminalActor = container.spawn("terminal-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          receivedMessages.add(message);
        }
      });

      final var actor = container.spawn("actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          pipeFuture(CompletableFuture.failedFuture(
              new RuntimeException("intentional failure"))).to(terminalActor);
        }
      });

      container.send("trigger").to(actor);

      final var probeReceived = new CopyOnWriteArrayList<String>();
      final var probeActor = container.spawn("probe-actor", () -> new Actor<String>() {
        @Override
        public void onMessage(final @NotNull String message) {
          probeReceived.add(message);
        }
      });

      container.send("probe").to(probeActor);

      await().atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(probeReceived).containsExactly("probe"));
      assertThat(receivedMessages).isEmpty();
    }
  }

  @Nested
  @DisplayName("given an actor that should respond asynchronously")
  class GivenAnActorThatShouldRespondAsynchronously {

    @Nested
    @DisplayName("when a request is received")
    class WhenRequestIsReceived {

      @Nested
      @DisplayName("when used from outside of the container")
      class WhenUsedFromOutsideOfTheContainer {

        @Nested
        @DisplayName("should complete the corresponding future when the operation is finished")
        class ShouldCompleteTheCorrespondingFutureWhenTheOperationIsFinished {

          @Test
          @DisplayName("when send is used to respond")
          void whenSendIsUsedToRespond(final @NotNull SlactTestContainer container)
              throws Exception {

            final var actor = container.spawn("actor", () -> new Actor<String>() {
              @Override
              public void onMessage(final @NotNull String message) {
                respondWith("Hi there!");
              }
            });

            final var eventualResponse = container.requestResponseTo("Hello world!")
                .ofType(String.class).from(actor);

            await().atMost(Duration.ofSeconds(5)).until(eventualResponse::isDone);

            assertThat(eventualResponse.get()).isEqualTo("Hi there!");
          }

          @Test
          @DisplayName("when respond is used to respond")
          void whenRespondIsUsedToRespond(final @NotNull SlactTestContainer container)
              throws Exception {

            final var actor = container.spawn("actor", () -> new Actor<String>() {
              @Override
              public void onMessage(final @NotNull String message) {
                send("Hi there!").to(sender());
              }
            });

            final var eventualResponse = container.requestResponseTo("Hello world!")
                .ofType(String.class).from(actor);

            await().atMost(Duration.ofSeconds(5)).until(eventualResponse::isDone);

            assertThat(eventualResponse.get()).isEqualTo("Hi there!");
          }
        }

        @Test
        @DisplayName("should complete the future with the correct result")
        void shouldCompleteTheFutureWithTheCorrectResult(
            final @NotNull SlactTestContainer container) throws Exception {

          final var actor = container.spawn("actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {

              try {
                Thread.sleep(1000);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }

              send(message).to(sender());
            }
          });

          container.send("First message for which the future should not be completed.").to(actor);

          final var eventualResponse = container.requestResponseTo(
                  "Second message for which the future should be completed.").ofType(String.class)
              .from(actor);

          await().atMost(Duration.ofSeconds(5)).until(eventualResponse::isDone);

          assertThat(eventualResponse.get()).startsWith("Second");
        }
      }

      @Nested
      @DisplayName("when used from inside of the container")
      class WhenUsedFromInsideOfTheContainer {

        @Test
        @DisplayName("when send is used to respond")
        void whenSendIsUsedToRespond(final @NotNull SlactTestContainer container) {

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
                pipeFuture(
                    container.requestResponseTo(message).ofType(String.class).from(actor)).to(
                    self());
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
        @DisplayName("when respond is used to respond")
        void whenRespondIsUsedToRespond(final @NotNull SlactTestContainer container) {

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
                final var eventualResult = container.requestResponseTo(message).ofType(String.class)
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
      }
    }
  }
}
