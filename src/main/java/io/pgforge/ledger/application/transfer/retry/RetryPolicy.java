package io.pgforge.ledger.application.transfer.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded retry policy with exponential backoff and ±25% jitter.
 * Pure value object — no Spring, no clock, no logging side effects.
 */
public record RetryPolicy(int maxAttempts, Duration baseBackoff, Duration maxBackoff) {

    public RetryPolicy {
        Objects.requireNonNull(baseBackoff, "baseBackoff");
        Objects.requireNonNull(maxBackoff, "maxBackoff");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, was " + maxAttempts);
        }
        if (baseBackoff.isNegative() || baseBackoff.isZero()) {
            throw new IllegalArgumentException("baseBackoff must be > 0, was " + baseBackoff);
        }
        if (maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException(
                    "maxBackoff must be >= baseBackoff (%s vs %s)".formatted(maxBackoff, baseBackoff));
        }
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(5, Duration.ofMillis(2), Duration.ofMillis(50));
    }

    /**
     * Returns a jittered backoff for the given 1-indexed attempt. Exponential growth capped by
     * {@link #maxBackoff}; jitter range is ±25% of the scaled value.
     */
    public Duration backoff(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1, was " + attempt);
        }
        long baseNanos = baseBackoff.toNanos();
        long maxNanos = maxBackoff.toNanos();
        int shift = Math.min(attempt - 1, 30);
        long scaled = Math.min(maxNanos, baseNanos << shift);
        long jitterRange = Math.max(1, scaled / 4);
        long jitter = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        long total = Math.max(baseNanos, scaled + jitter);
        return Duration.ofNanos(Math.min(total, maxNanos));
    }
}
