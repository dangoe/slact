package de.dangoe.concurrent.slact.core;

import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(SlactTestContainerExtension.class)
@EnabledIfEnvironmentVariable(named = "PERF_TEST", matches = "true")
public class ActorPerformanceTest {

  private static final class TestActor extends Actor<String> {

    @Override
    public void onMessage(final @NotNull String message) {
      // Empty - just consume the message
    }
  }

  private static final class CountingActor extends Actor<String> {

    private final AtomicLong messageCount = new AtomicLong(0);
    private final CountDownLatch latch;
    private final long expectedMessages;

    public CountingActor(long expectedMessages, CountDownLatch latch) {
      this.expectedMessages = expectedMessages;
      this.latch = latch;
    }

    @Override
    public void onMessage(final @NotNull String message) {
      long count = messageCount.incrementAndGet();
      if (count >= expectedMessages) {
        latch.countDown();
      }
    }
  }

  private static final @NotNull Logger logger = LoggerFactory.getLogger(ActorPerformanceTest.class);

  private static final int ACTOR_COUNT = readRequestedActorCount();

  @Test
  void testActorSpawningPerformance(final @NotNull SlactTestContainer container) throws Exception {

    final var start = Instant.now();

    logger.info("Spawning actors");

    final var futures = new ArrayList<Future<ActorHandle<?>>>();

    try (final var spawner = Executors.newWorkStealingPool()) {
      for (int i = 0; i < ACTOR_COUNT; i++) {
        final var currentIndex = i;
        futures.add(
            spawner.submit(() -> container.spawn(String.valueOf(currentIndex), TestActor::new)));
      }
    }

    final var spawned = Instant.now();

    await().atMost(Duration.ofMinutes(1))
        .until(() -> futures.stream().allMatch(Future::isDone));

    final var actors = futures.stream().map(it -> {
      try {
        return it.get();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    }).toList();

    container.awaitStartupComplete(actors.stream().map(ActorHandle::path).toList());

    final var ready = Instant.now();

    logger.info("Actors spawned in {} ms", Duration.between(start, spawned).toMillis());
    logger.info("Actors spawned and ready in {} ms", Duration.between(start, ready).toMillis());
    logger.info("Actor creation rate: {} actors/second",
        ACTOR_COUNT * 1000L / Math.max(1, Duration.between(start, ready).toMillis()));
  }

  @Test
  void testMessageThroughputPerformance(final @NotNull SlactTestContainer container)
      throws Exception {

    final int numActors = 10;
    final int messagesPerActor = 10;
    final long totalMessages = (long) numActors * messagesPerActor;

    logger.info("Starting message throughput test: {} actors, {} messages each, {} total messages",
        numActors, messagesPerActor, totalMessages);

    final var receivers = new ArrayList<ActorHandle<String>>();

    final var latch = new CountDownLatch(numActors);

    for (int i = 0; i < numActors; i++) {
      final var receiver = container.spawn(
          "receiver-" + i,
          () -> new CountingActor(messagesPerActor, latch)
      );
      receivers.add(receiver);
    }

    container.awaitStartupComplete(receivers.stream().map(ActorHandle::path).toList());

    final var start = Instant.now();

    final var futures = Collections.synchronizedList(new ArrayList<Future<?>>());

    try (final var executor = Executors.newFixedThreadPool(16)) {
      for (final var receiver : receivers) {
        futures.add(executor.submit(() -> {
          for (int i = 0; i < messagesPerActor; i++) {
            container.send("message-" + i).to(receiver);
          }
        }));
      }
    }

    await().atMost(Duration.ofSeconds(5))
        .until(() -> futures.stream().allMatch(it -> it.isDone() || it.isCancelled()));

    final var sentComplete = Instant.now();

    final var completed = latch.await(2, TimeUnit.MINUTES);

    final var processingComplete = Instant.now();

    if (!completed) {
      logger.error("Message processing timed out!");
      Assertions.fail("Not all messages were processed within timeout");
    }

    final var sendDurationMs = Duration.between(start, sentComplete).toMillis();
    final var totalDurationMs = Duration.between(start, processingComplete).toMillis();

    final var sendRate = totalMessages * 1000L / Math.max(1, sendDurationMs);
    final var processingRate = totalMessages * 1000L / Math.max(1, totalDurationMs);

    logger.info("=== Message Throughput Results ===");
    logger.info("Messages sent in {} ms", sendDurationMs);
    logger.info("Messages processed in {} ms", totalDurationMs);
    logger.info("Send rate: {} messages/second", sendRate);
    logger.info("Processing rate: {} messages/second", processingRate);
    logger.info("Average latency: {} microseconds",
        (totalDurationMs * 1000.0) / totalMessages);
  }

  @Test
  void testRequestResponsePerformance(final @NotNull SlactTestContainer container)
      throws Exception {

    final int numRequests = 100_000;

    logger.info("Starting request-response test: {} requests", numRequests);

    final var responder = container.spawn("responder", () -> new Actor<String>() {
      @Override
      public void onMessage(final @NotNull String message) {
        respondWith("response-" + message);
      }
    });

    container.awaitStartupComplete(List.of(responder.path()));

    final var start = Instant.now();

    // Send requests and collect futures
    final var responseFutures = new ArrayList<Future<String>>();
    for (int i = 0; i < numRequests; i++) {
      Future<String> response = container
          .requestResponseTo("request-" + i)
          .ofType(String.class)
          .from(responder);
      responseFutures.add(response);
    }

    for (var future : responseFutures) {
      future.get(5, TimeUnit.SECONDS);
    }

    final var complete = Instant.now();
    final long durationMs = Duration.between(start, complete).toMillis();
    final long requestsPerSecond = numRequests * 1000L / Math.max(1, durationMs);

    logger.info("=== Request-Response Results ===");
    logger.info("Completed {} request-response cycles in {} ms", numRequests, durationMs);
    logger.info("Request-response rate: {} requests/second", requestsPerSecond);
    logger.info("Average round-trip latency: {} microseconds",
        (durationMs * 1000.0) / numRequests);
  }

  private static int readRequestedActorCount() {

    final var environmentVariable = System.getenv("ACTOR_COUNT");

    if (environmentVariable == null) {
      return 1;
    }

    return Integer.parseInt(environmentVariable);
  }
}