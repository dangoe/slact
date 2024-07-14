package de.dangoe.slacktors;

import de.dangoe.slacktors.lib.AbstractActor;
import de.dangoe.slacktors.lib.ActorHandle;
import de.dangoe.slacktors.lib.Director;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class Main {

    public static class CounterActor extends AbstractActor<String> {

        private int counter = 0;
        private Instant lastTick = Instant.now();

        @Override
        protected void onMessage(String msg) {
            System.out.printf("Received %s from %s on %s%n", msg, sender(), Thread.currentThread());

            counter++;

            if (counter % 2 == 0) {
                final var now = Instant.now();
                long millis = Duration.between(lastTick, now).toMillis();
                System.out.println(millis);
                lastTick = now;
            }
        }
    }

    public static class MyActor extends AbstractActor<String> {

        @Override
        protected void onMessage(String msg) {
            final var maybeParent = self().path().parent();

            if (maybeParent.isPresent()) {
                maybeParent
                        .flatMap(context()::select)
                        .ifPresent(actor -> actor.send(msg, MyActor.this.self()));
            } else {
                context()
                        .select(counter.path())
                        .ifPresent(actor -> actor.send(msg, MyActor.this.self()));
            }
        }
    }

    private static final Director director = Director.forName(
            UUID.randomUUID().toString()
    );

    private static final ActorHandle<String> counter = director.actorOf(
            CounterActor::new
    );

    public static void main(String[] args) throws Exception {
        final var firstActor = director.actorOf(MyActor::new);
        final var nestedActor = firstActor.actorOf(MyActor::new);

        for (int i = 0; i < 10_000_000; i++) {
            firstActor.send("Hello world %s!".formatted(i), director);
            nestedActor.send("Hello world %s!".formatted(i), director);
        }

        System.out.println("Waiting for shutdown ...");

        Thread.sleep(10000);

        System.out.println("Shutting down ...");

        director.shutdown();
    }
}
