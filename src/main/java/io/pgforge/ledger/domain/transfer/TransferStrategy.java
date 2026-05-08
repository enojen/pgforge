package io.pgforge.ledger.domain.transfer;

/**
 * Outbound port: a single concurrency-control strategy for executing a transfer.
 * Implementations live in infrastructure and may differ in transaction boundary,
 * locking discipline, and retry behaviour.
 */
public interface TransferStrategy {

    TransferOutcome execute(TransferCommand command);

    TransferStrategyName name();
}
