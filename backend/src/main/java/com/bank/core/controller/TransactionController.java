package com.bank.core.controller;

import com.bank.core.dto.TransactionDto;
import com.bank.core.exception.ResourceNotFoundException;
import com.bank.core.model.Account;
import com.bank.core.model.Transaction;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import com.bank.core.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@Valid @RequestBody TransactionDto.Request request) {
        log.info("Deposit request: account={}, amount={}", request.getSourceAccountNumber(), request.getAmount());
        Transaction tx = transactionService.deposit(request.getSourceAccountNumber(), request.getAmount());
        return ResponseEntity.ok(buildResponse(tx));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@Valid @RequestBody TransactionDto.Request request) {
        log.info("Withdrawal request: account={}, amount={}", request.getSourceAccountNumber(), request.getAmount());
        Transaction tx = transactionService.withdraw(request.getSourceAccountNumber(), request.getAmount());
        return ResponseEntity.ok(buildResponse(tx));
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(
            @Valid @RequestBody TransactionDto.Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        log.info("Transfer request: source={}, target={}, amount={}, idempotencyKey={}",
                request.getSourceAccountNumber(), request.getTargetAccountNumber(),
                request.getAmount(), idempotencyKey);

        Transaction tx = transactionService.transfer(
                request.getSourceAccountNumber(),
                request.getTargetAccountNumber(),
                request.getAmount(),
                idempotencyKey,
                request.getDescription());

        return ResponseEntity.ok(buildResponse(tx));
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<?> getTransfer(@PathVariable Long transferId) {
        Transaction tx = transactionRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));
        return ResponseEntity.ok(buildResponse(tx));
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<Page<TransactionDto.Response>> getHistory(
            @PathVariable String accountNumber,
            @RequestParam(required = false) LocalDateTime start,
            @RequestParam(required = false) LocalDateTime end,
            Pageable pageable) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        Page<Transaction> transactions;
        if (start != null && end != null) {
            transactions = transactionRepository.findAllByAccountIdAndDateRange(account.getId(), start, end, pageable);
        } else {
            transactions = transactionRepository.findAllByAccountId(account.getId(), pageable);
        }

        return ResponseEntity.ok(transactions.map(this::buildResponse));
    }

    private TransactionDto.Response buildResponse(Transaction tx) {
        return TransactionDto.Response.builder()
                .id(tx.getId())
                .idempotencyKey(tx.getIdempotencyKey())
                .sourceAccountNumber(tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : null)
                .targetAccountNumber(tx.getTargetAccount() != null ? tx.getTargetAccount().getAccountNumber() : null)
                .amount(tx.getAmount())
                .type(tx.getType())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
