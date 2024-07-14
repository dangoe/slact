package de.dangoe.concurrent.slact;

import org.opentest4j.AssertionFailedError;

import java.time.Duration;
import java.time.Instant;

public class AsyncAsserts {

    @SuppressWarnings("BusyWait")
    public static void eventually(final Runnable check, final Duration interval, final Duration timeout) throws Exception {

        final var begin = Instant.now();

        while (true) {

            Thread.sleep(interval.toMillis());

            AssertionFailedError error = null;

            try {
                check.run();
            } catch (final AssertionFailedError e) {
                error = e;
            }

            if (error == null) {
                return;
            } else if (Instant.now().isAfter(begin.plus(timeout))) {
                throw error;
            }
        }
    }
}

