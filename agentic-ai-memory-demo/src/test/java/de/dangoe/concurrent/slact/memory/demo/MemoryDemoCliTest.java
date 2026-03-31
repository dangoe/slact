package de.dangoe.concurrent.slact.memory.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dangoe.concurrent.slact.memory.PromptResponse;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryDemoCli")
class MemoryDemoCliTest {

  @Test
  @DisplayName("should stop when quit command is entered")
  void shouldStopWhenQuitCommandIsEntered() throws Exception {
    final var output = new ArrayList<String>();
    final var processedCount = new AtomicInteger(0);
    final var reader = readerFor("\nquit\nhello\n");

    MemoryDemoCli.runCliLoop(reader, output::add, prompt -> {
      processedCount.incrementAndGet();
      return new PromptResponse.Answer("ignored");
    });

    assertEquals(0, processedCount.get());
    assertTrue(output.isEmpty());
  }

  @Test
  @DisplayName("should stop when exit command is entered")
  void shouldStopWhenExitCommandIsEntered() throws Exception {
    final var output = new ArrayList<String>();
    final var processedCount = new AtomicInteger(0);
    final var reader = readerFor("exit\nhello\n");

    MemoryDemoCli.runCliLoop(reader, output::add, prompt -> {
      processedCount.incrementAndGet();
      return new PromptResponse.Answer("ignored");
    });

    assertEquals(0, processedCount.get());
    assertTrue(output.isEmpty());
  }

  @Test
  @DisplayName("should process prompt and print answer")
  void shouldProcessPromptAndPrintAnswer() throws Exception {
    final List<String> output = new ArrayList<>();
    final var reader = readerFor("  hello world  \n");

    MemoryDemoCli.runCliLoop(reader, output::add,
        prompt -> new PromptResponse.Answer("ok: " + prompt));

    assertEquals(List.of("Answer: ok: hello world"), output);
  }

  @Test
  @DisplayName("should print error output for failure response")
  void shouldPrintErrorOutputForFailureResponse() throws Exception {
    final List<String> output = new ArrayList<>();
    final var reader = readerFor("question\n");

    MemoryDemoCli.runCliLoop(reader, output::add, prompt -> new PromptResponse.Failure("boom"));

    assertEquals(List.of("Error: boom"), output);
  }

  private static BufferedReader readerFor(final String input) {
    return new BufferedReader(new StringReader(input));
  }
}
