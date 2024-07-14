package de.dangoe.slacktors;

import de.dangoe.slacktors.lib.AbstractActor;
import de.dangoe.slacktors.lib.ActorHandle;
import de.dangoe.slacktors.lib.Director;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class Main {

    public static class CounterActor extends AbstractActor<Object> {

        private int counter = 0;
        private Instant lastTick = Instant.now();

        @Override
        protected void onMessage(Object msg) {
            System.out.println("Received %s from %s".formatted(msg, sender()));

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

    private static final ActorHandle<Object> counter = director.newActor(
        CounterActor::new
    );

    public static void main(String[] args) throws Exception {
        final var firstActor = director.newActor(MyActor::new);
        final var nestedActor = firstActor.newActor(MyActor::new);

        for (int i = 0; i < 1000; i++) {
            firstActor.send("Hello world %s!".formatted(i), director);
            nestedActor.send("Hello world %s!".formatted(i), director);
            Thread.sleep(10);
        }

        Thread.sleep(60000);

        director.shutdown();
    }
}
