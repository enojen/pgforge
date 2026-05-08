package io.pgforge.ledger.infrastructure.web;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.pgforge.ledger.domain.account.AccountNotFoundException;
import io.pgforge.ledger.domain.account.InsufficientFundsException;
import io.pgforge.ledger.domain.transfer.OptimisticRetryExhaustedException;

@RestControllerAdvice
public class LedgerExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail onInsufficientFunds(InsufficientFundsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Insufficient funds");
        pd.setProperty("accountId", ex.accountId().value());
        pd.setProperty("balance", ex.balance().amount());
        pd.setProperty("requested", ex.requested().amount());
        return pd;
    }

    @ExceptionHandler(OptimisticRetryExhaustedException.class)
    public ProblemDetail onRetryExhausted(OptimisticRetryExhaustedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Optimistic retry exhausted");
        pd.setProperty("attempts", ex.attempts());
        return pd;
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ProblemDetail onLockConflict(CannotAcquireLockException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Database lock conflict; the request was not committed.");
        pd.setTitle("Lock conflict");
        return pd;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail onAccountNotFound(AccountNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Account not found");
        pd.setProperty("accountId", ex.accountId().value());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid request");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getBindingResult().toString());
        pd.setTitle("Validation failed");
        return pd;
    }
}
