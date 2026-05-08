package io.pgforge.ledger.application.transfer.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void defaults_returns_5attempts_2msBase_50msMax() {
        RetryPolicy p = RetryPolicy.defaults();
        assertThat(p.maxAttempts()).isEqualTo(5);
        assertThat(p.baseBackoff()).isEqualTo(Duration.ofMillis(2));
        assertThat(p.maxBackoff()).isEqualTo(Duration.ofMillis(50));
    }

    @Test
    void rejectsZeroOrNegativeMaxAttempts() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofMillis(1), Duration.ofMillis(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroBaseBackoff() {
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ZERO, Duration.ofMillis(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMaxLessThanBase() {
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofMillis(10), Duration.ofMillis(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @RepeatedTest(20)
    void backoff_neverExceedsMax_andStaysAboveBaseFloor() {
        RetryPolicy p = RetryPolicy.defaults();
        for (int attempt = 1; attempt <= p.maxAttempts(); attempt++) {
            Duration b = p.backoff(attempt);
            assertThat(b).isLessThanOrEqualTo(p.maxBackoff());
            assertThat(b).isGreaterThanOrEqualTo(p.baseBackoff());
        }
    }

    @Test
    void backoff_growsBeyondBase_byThirdAttempt() {
        RetryPolicy p = new RetryPolicy(5, Duration.ofMillis(2), Duration.ofMillis(500));
        // Attempt 3 has exponential factor 4× before jitter — well above the base floor.
        Duration b3 = p.backoff(3);
        assertThat(b3).isGreaterThan(Duration.ofMillis(4));
    }

    @Test
    void backoff_rejectsZeroOrNegativeAttempt() {
        assertThatThrownBy(() -> RetryPolicy.defaults().backoff(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
