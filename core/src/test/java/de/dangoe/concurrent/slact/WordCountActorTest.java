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
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class WordCountActorTest {

  private sealed interface WordCountActorMessage {

  }

  private record ProcessFileCommand(@NotNull String fileName) implements WordCountActorMessage {

  }

  private record WordCount(int lineNumber, int words) implements WordCountActorMessage {

  }

  private record Line(int number, String content) {

  }

  private final SlactContainer container = new SlactContainerBuilder().build();

  @Nested
  class GivenAnWordCountingActor {

    @Nested
    class WhenWordsOfGivenDocumentShouldBeCounted {

      @Test
      void TheActorShouldCountTheWordsAccordingly() {

        final var wordCount = new AtomicInteger(0);

        final var wordCounterActor = container.spawn("word-counter-actor",
            () -> new Actor<WordCountActorMessage>() {

              private Integer maxLines;

              private ActorHandle<Line> lineProcessor;

              @Override
              public void onMessage(final @NotNull WordCountActorMessage message) {

                if (message instanceof ProcessFileCommand processFileCommand) {

                  this.lineProcessor = context().spawn(() -> new Actor<Line>() {

                    @Override
                    public void onMessage(final @NotNull Line line) {
                      send(new WordCount(line.number(), line.content().split(" ").length)).to(
                          sender());
                    }
                  });

                  try {
                    final var lines = Files.readAllLines(Paths.get(Objects.requireNonNull(
                        getClass().getResource(processFileCommand.fileName())).toURI()));

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

                if (message instanceof WordCount castedMessage) {

                  wordCount.addAndGet(castedMessage.words());

                  if (castedMessage.lineNumber + 1 == maxLines) {
                    context().exterminate(lineProcessor);
                    behaveAs(this);
                  }
                } else {
                  reject(message);
                }
              }
            });

        container.send((WordCountActorMessage) new ProcessFileCommand("lorem-ipsum.txt"))
            .to(wordCounterActor);

        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(wordCount.get()).isEqualTo(9895));
      }
    }
  }
}
