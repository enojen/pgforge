package io.pgforge.ledger.domain.transfer;

public enum TransferStrategyName {
    NAIVE,
    PESSIMISTIC,
    OPTIMISTIC;

    public static TransferStrategyName fromExternal(String value) {
        if (value == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        return TransferStrategyName.valueOf(value.trim().toUpperCase());
    }
}
