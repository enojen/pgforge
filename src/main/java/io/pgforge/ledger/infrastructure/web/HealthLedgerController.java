package io.pgforge.ledger.infrastructure.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.pgforge.ledger.application.account.AccountService;

@RestController
@RequestMapping("/health")
public class HealthLedgerController {

    private final AccountService accountService;

    public HealthLedgerController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/ledger")
    public Map<String, Object> ledgerHealth() {
        return Map.of("ok", true, "accounts", accountService.count());
    }
}
