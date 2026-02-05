package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.model.ReceivedMessage;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
public class ActorAsyncResponseTest {

  @Nested
  class GivenAnActorThatWorksWithResultsProvidedAsynchronously {

    @Test
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

        await().atMost(Duration.ofSeconds(1000)).untilAsserted(
            () -> assertThat(result).containsExactlyInAnyOrderElementsOf(messages.stream()
                .map(message -> new ReceivedMessage<>(message, ActorPath.root().append("actor")))
                .toList()));
      }
    }
  }

  @Nested
  class GivenAnActorThatShouldRespondAsynchronously {

    @Nested
    class WhenRequestIsReceived {

      @Nested
      class WhenUsedFromOutsideOfTheContainer {

        @Nested
        class ShouldCompleteTheCorrespondingFutureWhenTheOperationIsFinished {

          @Test
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
        void shouldCompleteTheFutureWithTheCorrectResult(
            final @NotNull SlactTestContainer container) throws Exception {

          final var actor = container.spawn("actor", () -> new Actor<String>() {
            @Override
            public void onMessage(final @NotNull String message) {

              // Wait to ensure that both messages are scheduled
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
      class WhenUsedFromInsideOfTheContainer {


        @Test
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
