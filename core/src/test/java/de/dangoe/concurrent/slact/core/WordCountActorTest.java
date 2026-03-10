package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor;
import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor.RoutingRequest;
import de.dangoe.concurrent.slact.core.patterns.actors.RoutingActor.SimpleRoutingRequest;
import de.dangoe.concurrent.slact.testkit.Constants;
import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Given a word counting actor")
@ExtendWith(SlactTestContainerExtension.class)
public class WordCountActorTest {

  private sealed interface WordCountActorMessage {

  }

  private record ProcessFileCommand(@NotNull String fileName) implements WordCountActorMessage {

  }

  private record WordCountResult(int wordCount) implements WordCountActorMessage {

  }

  private record LineWordCount(int lineNumber, int words) implements WordCountActorMessage {

  }

  private record Line(int number, String content) {

  }

  @Nested
  @DisplayName("When words of a given document should be counted")
  class WhenWordsOfGivenDocumentShouldBeCounted {

    @Test
    @DisplayName("Then the actor should count the words accordingly")
    void ThenTheActorShouldCountTheWordsAccordingly(final @NotNull SlactTestContainer container)
        throws Exception {

      final var wordCounterActor = container.spawn("word-counter-actor",
          () -> new Actor<WordCountActorMessage>() {

            private final @NotNull Map<Integer, Integer> lineWordCounts = new HashMap<>();

            private @Nullable Integer maxLines = null;

            private @Nullable ActorHandle<WordCountResult> commandSender;
            private @Nullable ActorHandle<SimpleRoutingRequest<Line>> lineProcessor;

            @Override
            public void onMessage(final @NotNull WordCountActorMessage message) {

              if (message instanceof ProcessFileCommand(String fileName)) {

                this.commandSender = sender();

                this.lineProcessor = context().spawn(
                    RoutingActor.roundRobinWorker(10, () -> new Actor<Line>() {

                      @Override
                      public void onMessage(final @NotNull Line line) {
                        send(new LineWordCount(line.number(), line.content().split(" ").length)).to(
                            sender());
                      }
                    }));

                behaveAs(this::processing);

                try (final var lines = Files.lines(
                    Paths.get(Objects.requireNonNull(getClass().getResource(fileName)).toURI()))) {

                  final var lineNumber = new AtomicInteger();

                  lines.forEach(line -> {
                    if (this.lineProcessor != null) {
                      send((RoutingRequest<Line>) new SimpleRoutingRequest<>(
                          new Line(lineNumber.getAndIncrement(), line))).to(this.lineProcessor);
                    }
                  });

                  maxLines = lineNumber.get();
                } catch (IOException | URISyntaxException e) {
                  fail(e);
                }
              } else {
                reject(message);
              }
            }

            private void processing(final WordCountActorMessage message) {

              if (message instanceof LineWordCount(int lineNumber, int words)) {

                lineWordCounts.put(lineNumber, words);

                if (maxLines != null && lineWordCounts.size() == maxLines) {

                  if (this.lineProcessor != null) {
                    context().stop(this.lineProcessor);
                  } else {
                    fail("Line processor is null!");
                  }

                  if (this.commandSender != null) {
                    send(new WordCountResult(
                        lineWordCounts.values().stream().reduce(0, Integer::sum))).to(
                        this.commandSender);
                  } else {
                    fail("Command sender is null!");
                  }

                  reset();
                }
              } else {
                reject(message);
              }
            }

            private void reset() {
              this.commandSender = null;
              this.maxLines = null;
              this.lineWordCounts.clear();
              behaveAsDefault();
            }
          });

      final var eventualResponse = container.requestResponseTo(
              (WordCountActorMessage) new ProcessFileCommand("lorem-ipsum.txt"))
          .ofType(WordCountResult.class).from(wordCounterActor);

      await().atMost(Constants.DEFAULT_TIMEOUT).until(eventualResponse::isDone);

      assertThat(eventualResponse.get()).isEqualTo(new WordCountResult(9895));
    }
  }
}
