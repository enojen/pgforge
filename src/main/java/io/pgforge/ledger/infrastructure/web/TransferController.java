package io.pgforge.ledger.infrastructure.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.pgforge.ledger.application.transfer.TransferService;
import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.Money;
import io.pgforge.ledger.domain.transfer.TransferCommand;
import io.pgforge.ledger.domain.transfer.TransferOutcome;
import io.pgforge.ledger.domain.transfer.TransferStrategyName;
import io.pgforge.ledger.infrastructure.web.dto.TransferRequestDto;
import io.pgforge.ledger.infrastructure.web.dto.TransferResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/naive")
    public TransferResponse naive(@Valid @RequestBody TransferRequestDto req) {
        return run(TransferStrategyName.NAIVE, req);
    }

    @PostMapping("/pessimistic")
    public TransferResponse pessimistic(@Valid @RequestBody TransferRequestDto req) {
        return run(TransferStrategyName.PESSIMISTIC, req);
    }

    @PostMapping("/optimistic")
    public TransferResponse optimistic(@Valid @RequestBody TransferRequestDto req) {
        return run(TransferStrategyName.OPTIMISTIC, req);
    }

    private TransferResponse run(TransferStrategyName name, TransferRequestDto req) {
        TransferCommand cmd = new TransferCommand(
                AccountId.of(req.fromAccountId()),
                AccountId.of(req.toAccountId()),
                Money.of(req.amount()));
        TransferOutcome outcome = transferService.transfer(name, cmd);
        return TransferResponse.of(outcome);
    }
}
