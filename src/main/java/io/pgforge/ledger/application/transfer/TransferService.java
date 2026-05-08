package io.pgforge.ledger.application.transfer;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import io.pgforge.ledger.domain.transfer.TransferCommand;
import io.pgforge.ledger.domain.transfer.TransferOutcome;
import io.pgforge.ledger.domain.transfer.TransferStrategy;
import io.pgforge.ledger.domain.transfer.TransferStrategyName;

/**
 * Routes a {@link TransferCommand} to the requested {@link TransferStrategy}.
 * Pure POJO — does not open transactions or touch persistence directly.
 */
public class TransferService {

    private final Map<TransferStrategyName, TransferStrategy> strategies;

    public TransferService(Map<TransferStrategyName, TransferStrategy> strategies) {
        Objects.requireNonNull(strategies, "strategies");
        EnumMap<TransferStrategyName, TransferStrategy> copy = new EnumMap<>(TransferStrategyName.class);
        copy.putAll(strategies);
        if (copy.size() != TransferStrategyName.values().length) {
            throw new IllegalArgumentException(
                    "every TransferStrategyName must have a strategy; got " + copy.keySet());
        }
        this.strategies = copy;
    }

    public TransferOutcome transfer(TransferStrategyName name, TransferCommand command) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(command, "command");
        TransferStrategy strategy = strategies.get(name);
        if (strategy == null) {
            throw new IllegalStateException("no strategy registered for " + name);
        }
        return strategy.execute(command);
    }
}
