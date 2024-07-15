package slact;

import de.dangoe.concurrent.slact.Actor;
import de.dangoe.concurrent.slact.ActorPath;
import de.dangoe.concurrent.slact.Slact;

import java.time.Duration;
import java.time.Instant;

public class Main {

    public static class CounterActor extends Actor<String> {

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

    public static class MyActor extends Actor<String> {

        @Override
        protected void onMessage(String msg) {
            final var maybeParent = self().path().parent();

            if (maybeParent.isPresent()) {
                maybeParent.flatMap(context()::resolve).ifPresent(actor -> actor.send(msg, MyActor.this.self()));
            } else {
                context().resolve(ActorPath.root().append("counter")).ifPresent(actor -> actor.send(msg, MyActor.this.self()));
            }
        }
    }

    public static void main(String[] args) throws Exception {

        final var actorsRuntime = Slact.createRuntime("container");

        actorsRuntime.register("counter", CounterActor::new);

        final var firstActor = actorsRuntime.register(MyActor::new);
        final var nestedActor = firstActor.register(MyActor::new);

        for (int i = 0; i < 10_000_000; i++) {
            firstActor.send("Hello world %s!".formatted(i), actorsRuntime);
            nestedActor.send("Hello world %s!".formatted(i), actorsRuntime);
        }

        System.out.println("Waiting for shutdown ...");

        Thread.sleep(10000);

        System.out.println("Shutting down ...");

        actorsRuntime.shutdown();
    }
}
