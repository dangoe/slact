package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testsupport.SlactTestContainer;
import de.dangoe.concurrent.slact.testsupport.SlactTestContainerExtension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
  class GivenAnWordCountingActor {

    @Nested
    class WhenWordsOfGivenDocumentShouldBeCounted {

      @Test
      void TheActorShouldCountTheWordsAccordingly(final @NotNull SlactTestContainer container) throws Exception {

        final var wordCount = new AtomicInteger(0);

        final var wordCounterActor = container.spawn("word-counter-actor",
            () -> new Actor<WordCountActorMessage>() {

              private Integer maxLines;

              private ActorHandle<WordCountResult> commandSender;
              private ActorHandle<? extends Line> lineProcessor;

              @Override
              public void onMessage(final @NotNull WordCountActorMessage message) {

                if (message instanceof ProcessFileCommand(String fileName)) {

                  this.commandSender = sender();

                  this.lineProcessor = context().spawn(() -> new Actor<Line>() {

                    @Override
                    public void onMessage(final @NotNull Line line) {
                      send(new LineWordCount(line.number(), line.content().split(" ").length)).to(
                          sender());
                    }
                  });

                  try {
                    final var lines = Files.readAllLines(Paths.get(
                        Objects.requireNonNull(getClass().getResource(fileName)).toURI()));

                    this.maxLines = lines.size();

                    behaveAs(this::processing);

                    int lineNumber = 0;

                    for (final var line : lines) {
                      send(new Line(lineNumber, line)).to(this.lineProcessor);
                      lineNumber++;
                    }
                  } catch (IOException | URISyntaxException e) {
                    fail(e);
                  }
                } else {
                  reject(message);
                }
              }

              private void processing(final WordCountActorMessage message) {

                if (message instanceof LineWordCount(int lineNumber, int words)) {

                  wordCount.addAndGet(words);

                  if (lineNumber + 1 == maxLines) {
                    context().stop(lineProcessor);
                    send(new WordCountResult(wordCount.get())).to(this.commandSender);
                    this.commandSender = null;
                    behaveAsDefault();
                  }
                } else {
                  reject(message);
                }
              }
            });

        final var eventualResponse = container.requestResponseTo(
                (WordCountActorMessage) new ProcessFileCommand("lorem-ipsum.txt"))
            .ofType(WordCountResult.class).from(wordCounterActor);

        await().atMost(Duration.ofSeconds(5)).until(eventualResponse::isDone);

        assertThat(eventualResponse.get()).isEqualTo(new WordCountResult(9895));
      }
    }
  }
}
