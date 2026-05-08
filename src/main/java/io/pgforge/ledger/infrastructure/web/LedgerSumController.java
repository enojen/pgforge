package io.pgforge.ledger.infrastructure.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.pgforge.ledger.application.account.AccountService;
import io.pgforge.ledger.infrastructure.web.dto.LedgerSumResponse;

@RestController
@RequestMapping("/ledger")
public class LedgerSumController {

    private final AccountService accountService;

    public LedgerSumController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/sum")
    public LedgerSumResponse sum() {
        return LedgerSumResponse.of(accountService.sum());
    }
}
