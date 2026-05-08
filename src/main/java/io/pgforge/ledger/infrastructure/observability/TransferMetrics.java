package io.pgforge.ledger.infrastructure.observability;

import org.springframework.stereotype.Component;

import io.pgforge.ledger.domain.transfer.TransferStrategyName;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Centralised Micrometer instrumentation for the three transfer strategies.
 * Single source of truth for tag values so PromQL stays predictable.
 */
@Component
public class TransferMetrics {

    public static final String TIMER = "ledger.transfer";
    public static final String RETRIES = "ledger.transfer.retries";
    public static final String EXHAUSTIONS = "ledger.transfer.exhaustions";
    public static final String DEADLOCKS = "ledger.transfer.deadlocks";
    public static final String INSUFFICIENT_FUNDS = "ledger.transfer.insufficient_funds";

    private final Timer naiveTimer;
    private final Timer pessimisticTimer;
    private final Timer optimisticTimer;
    private final Counter optimisticRetries;
    private final Counter optimisticExhaustions;
    private final Counter pessimisticDeadlocks;
    private final Counter insufficientFunds;

    public TransferMetrics(MeterRegistry registry) {
        this.naiveTimer = buildTimer(registry, TransferStrategyName.NAIVE);
        this.pessimisticTimer = buildTimer(registry, TransferStrategyName.PESSIMISTIC);
        this.optimisticTimer = buildTimer(registry, TransferStrategyName.OPTIMISTIC);
        this.optimisticRetries = Counter.builder(RETRIES)
                .tag("strategy", TransferStrategyName.OPTIMISTIC.name().toLowerCase())
                .register(registry);
        this.optimisticExhaustions = Counter.builder(EXHAUSTIONS)
                .tag("strategy", TransferStrategyName.OPTIMISTIC.name().toLowerCase())
                .register(registry);
        this.pessimisticDeadlocks = Counter.builder(DEADLOCKS)
                .tag("strategy", TransferStrategyName.PESSIMISTIC.name().toLowerCase())
                .register(registry);
        this.insufficientFunds = Counter.builder(INSUFFICIENT_FUNDS).register(registry);
    }

    private static Timer buildTimer(MeterRegistry registry, TransferStrategyName strategy) {
        return Timer.builder(TIMER)
                .tag("strategy", strategy.name().toLowerCase())
                .publishPercentileHistogram()
                .register(registry);
    }

    public Timer timerFor(TransferStrategyName name) {
        return switch (name) {
            case NAIVE -> naiveTimer;
            case PESSIMISTIC -> pessimisticTimer;
            case OPTIMISTIC -> optimisticTimer;
        };
    }

    public Counter optimisticRetries() {
        return optimisticRetries;
    }

    public Counter optimisticExhaustions() {
        return optimisticExhaustions;
    }

    public Counter pessimisticDeadlocks() {
        return pessimisticDeadlocks;
    }

    public Counter insufficientFunds() {
        return insufficientFunds;
    }
}
