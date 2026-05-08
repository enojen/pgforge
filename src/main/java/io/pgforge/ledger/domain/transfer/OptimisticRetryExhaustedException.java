package io.pgforge.ledger.domain.transfer;

public class OptimisticRetryExhaustedException extends RuntimeException {

    private final int attempts;

    public OptimisticRetryExhaustedException(int attempts) {
        super("optimistic transfer exhausted after %d attempts".formatted(attempts));
        this.attempts = attempts;
    }

    public int attempts() {
        return attempts;
    }
}
