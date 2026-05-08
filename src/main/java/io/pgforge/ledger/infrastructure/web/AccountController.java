package io.pgforge.ledger.infrastructure.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.pgforge.ledger.application.account.AccountService;
import io.pgforge.ledger.domain.account.Account;
import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.Money;
import io.pgforge.ledger.infrastructure.web.dto.AccountResponse;
import io.pgforge.ledger.infrastructure.web.dto.CreateAccountRequestDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequestDto req) {
        Account account = accountService.create(req.name(), Money.of(req.initialBalance()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.id().value())
                .toUri();
        return ResponseEntity.created(location).body(AccountResponse.of(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable long id) {
        return AccountResponse.of(accountService.get(AccountId.of(id)));
    }
}
