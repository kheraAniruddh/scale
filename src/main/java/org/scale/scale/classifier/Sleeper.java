package org.scale.scale.classifier;

import java.time.Duration;

/** Waits between retries. Swapped for a no-op in tests so they never sleep. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration);

    static Sleeper threadSleeper() {
        return duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to retry", e);
            }
        };
    }
}
