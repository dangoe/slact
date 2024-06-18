package de.dangoe.slacktors;

import de.dangoe.slacktors.lib.AbstractActor;
import de.dangoe.slacktors.lib.ActorHandle;
import de.dangoe.slacktors.lib.ActorPath;
import de.dangoe.slacktors.lib.Director;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

public class Main {

    public static class CounterActor extends AbstractActor<Object> {

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

    public static class MyActor extends AbstractActor<Object> {

        @Override
        protected void onMessage(Object msg) {

            final var maybeParent = self().path().parent();

            if (maybeParent.isPresent()) {
                maybeParent.flatMap(context()::select).ifPresent(actor -> actor.send(msg));
            } else {
                context().select(counter.path()).ifPresent(actor -> actor.send(msg));
            }
        }
    }

    private static final Director system = Director.forName(UUID.randomUUID().toString());

    private static final ActorHandle<Object> counter = system.actorOf(CounterActor::new);

    public static void main(String[] args) {


        final var firstActor = system.actorOf(MyActor::new);
        final var nestedActor = firstActor.actorOf(MyActor::new);

        while (true) {

            firstActor.send("Hello world!");
            nestedActor.send("Hello world!");

        }
    }
}