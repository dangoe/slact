package de.dangoe.slacktors;

import de.dangoe.slacktors.lib.Actor;
import de.dangoe.slacktors.lib.ActorHandle;
import de.dangoe.slacktors.lib.Director;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

public class Main {

    public static class CounterActor extends Actor<Object> {

        private int counter = 0;
        private Instant lastTick = Instant.now();

        @Override
        protected void onMessage(Object msg) {
            counter++;

            if (counter % 1000 == 0) {
                final var now = Instant.now();
                long millis = Duration.between(lastTick, now).toMillis();
                System.out.println(millis);
                lastTick = now;
            }
        }
    }

    public static class MyActor extends Actor<Object> {

        @Override
        protected void onMessage(Object msg) {
            System.out.println("I've received %s for path %s.".formatted(msg, self().path()));
            context().select(counter.path()).send(msg);
        }
    }

    private static final Director system = Director.forName(UUID.randomUUID().toString());

    private static final ActorHandle<Object> counter = system.actorOf(CounterActor.class);

    public static void main(String[] args) {


        final var actors = IntStream.range(0, 10).boxed().map(index -> system.actorOf(MyActor.class)).toList();

        while (true) {

            actors.forEach(actor -> actor.send("Hello world!"));

        }
    }
}