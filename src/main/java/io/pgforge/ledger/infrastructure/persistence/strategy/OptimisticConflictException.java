package io.pgforge.ledger.infrastructure.persistence.strategy;

/**
 * Internal signal: a CAS update touched zero rows because another transaction
 * advanced the version. Caught by the strategy's retry loop; never escapes.
 */
class OptimisticConflictException extends RuntimeException {

    OptimisticConflictException(String detail) {
        super(detail);
    }
}
