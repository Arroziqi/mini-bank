package com.bank.core.controller;

import com.bank.core.dto.TransactionDto;
import com.bank.core.model.Account;
import com.bank.core.model.Transaction;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import com.bank.core.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody TransactionDto.Request request) {
        transactionService.deposit(request.getSourceAccountNumber(), request.getAmount());
        return ResponseEntity.ok("Deposit successful");
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody TransactionDto.Request request) {
        transactionService.withdraw(request.getSourceAccountNumber(), request.getAmount());
        return ResponseEntity.ok("Withdrawal successful");
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody TransactionDto.Request request) {
        transactionService.transfer(request.getSourceAccountNumber(), request.getTargetAccountNumber(),
                request.getAmount());
        return ResponseEntity.ok("Transfer successful");
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<Page<TransactionDto.Response>> getHistory(
            @PathVariable String accountNumber,
            @RequestParam(required = false) LocalDateTime start,
            @RequestParam(required = false) LocalDateTime end,
            Pageable pageable) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Page<Transaction> transactions;
        if (start != null && end != null) {
            transactions = transactionRepository.findAllByAccountIdAndDateRange(account.getId(), start, end, pageable);
        } else {
            transactions = transactionRepository.findAllByAccountId(account.getId(), pageable);
        }

        return ResponseEntity.ok(transactions.map(tx -> TransactionDto.Response.builder()
                .id(tx.getId())
                .sourceAccountNumber(tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : null)
                .targetAccountNumber(tx.getTargetAccount() != null ? tx.getTargetAccount().getAccountNumber() : null)
                .amount(tx.getAmount())
                .type(tx.getType())
                .createdAt(tx.getCreatedAt())
                .build()));
    }
}
