package de.dangoe.concurrent.slact;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


class ActorChainTest {

    private final Slact slact = Slact.createRuntime();

    @Test
    void messagesCanBeResend() throws Exception {

        final var result = new CopyOnWriteArrayList<>();

        final var terminalActor = slact.register(() -> new Actor<String>() {
            @Override
            protected void onMessage(final String message) {
                result.add(message);
            }
        });

        final var actor = slact.register(() -> new Actor<String>() {
            @Override
            protected void onMessage(final String message) {
                terminalActor.send(message, context().self());
            }
        });

        final var messages = IntStream.range(0, 100).boxed().map("m_%d"::formatted).toList();

        for (final var message : messages) {
            actor.send(message, slact);
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(result).containsExactlyElementsOf(messages));
    }
}
