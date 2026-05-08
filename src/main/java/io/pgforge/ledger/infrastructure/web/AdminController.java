package io.pgforge.ledger.infrastructure.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.pgforge.ledger.infrastructure.persistence.LedgerReset;

/**
 * Administrative endpoints, only registered when {@code pgforge.admin.enabled=true}.
 * Used by benchmarks to reset ledger state between runs; not for production use.
 */
@RestController
@RequestMapping("/admin")
@ConditionalOnProperty(prefix = "pgforge.admin", name = "enabled", havingValue = "true")
public class AdminController {

    private final LedgerReset ledgerReset;

    public AdminController(LedgerReset ledgerReset) {
        this.ledgerReset = ledgerReset;
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        ledgerReset.resetLedger();
        return ResponseEntity.noContent().build();
    }
}
