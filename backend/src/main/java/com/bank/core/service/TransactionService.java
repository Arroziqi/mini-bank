package com.bank.core.service;

import com.bank.core.exception.InsufficientBalanceException;
import com.bank.core.exception.ResourceNotFoundException;
import com.bank.core.model.Account;
import com.bank.core.model.LedgerEntry;
import com.bank.core.model.Transaction;
import com.bank.core.model.TransferStatus;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final LedgerService ledgerService;
    private final OutboxService outboxService;

    @Transactional
    public Transaction deposit(String accountNumber, BigDecimal amount) {
        log.info("Deposit initiated: account={}, amount={}", accountNumber, amount);

        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        BigDecimal oldBalance = account.getBalance();
        account.setBalance(oldBalance.add(amount));
        accountRepository.save(account);

        String idempotencyKey = UUID.randomUUID().toString();
        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .targetAccount(account)
                .amount(amount)
                .type(Transaction.Type.DEPOSIT)
                .status(TransferStatus.COMPLETED)
                .build();
        tx = transactionRepository.save(tx);

        ledgerService.createSingleEntry(tx, account, LedgerEntry.EntryType.CREDIT, amount);

        outboxService.publishEvent("TRANSACTION", tx.getId(), "DepositCompleted",
                String.format("{\"transactionId\":%d,\"accountNumber\":\"%s\",\"amount\":%s}", tx.getId(), accountNumber, amount));

        auditService.log("DEPOSIT", "Deposited " + amount + " to " + accountNumber,
                account.getUser());

        log.info("Deposit completed: account={}, oldBalance={}, newBalance={}, amount={}",
                accountNumber, oldBalance, account.getBalance(), amount);
        return tx;
    }

    @Transactional
    public Transaction withdraw(String accountNumber, BigDecimal amount) {
        log.info("Withdrawal initiated: account={}, amount={}", accountNumber, amount);

        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (account.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: account={}, balance={}, requested={}",
                    accountNumber, account.getBalance(), amount);
            throw new InsufficientBalanceException("Insufficient balance");
        }

        BigDecimal oldBalance = account.getBalance();
        account.setBalance(oldBalance.subtract(amount));
        accountRepository.save(account);

        String idempotencyKey = UUID.randomUUID().toString();
        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccount(account)
                .amount(amount)
                .type(Transaction.Type.WITHDRAWAL)
                .status(TransferStatus.COMPLETED)
                .build();
        tx = transactionRepository.save(tx);

        ledgerService.createSingleEntry(tx, account, LedgerEntry.EntryType.DEBIT, amount);

        outboxService.publishEvent("TRANSACTION", tx.getId(), "WithdrawalCompleted",
                String.format("{\"transactionId\":%d,\"accountNumber\":\"%s\",\"amount\":%s}", tx.getId(), accountNumber, amount));

        auditService.log("WITHDRAWAL", "Withdrew " + amount + " from " + accountNumber,
                account.getUser());

        log.info("Withdrawal completed: account={}, oldBalance={}, newBalance={}, amount={}",
                accountNumber, oldBalance, account.getBalance(), amount);
        return tx;
    }

    @Transactional
    public Transaction transfer(String sourceAccountNumber, String targetAccountNumber, BigDecimal amount,
                                String idempotencyKey, String description) {
        log.info("Transfer initiated: source={}, target={}, amount={}, idempotencyKey={}",
                sourceAccountNumber, targetAccountNumber, amount, idempotencyKey);

        // 1. Idempotency check
        if (idempotencyKey != null) {
            Transaction existing = transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                log.info("Idempotent request detected: key={}, existingTxId={}, status={}",
                        idempotencyKey, existing.getId(), existing.getStatus());
                return existing;
            }
        } else {
            idempotencyKey = UUID.randomUUID().toString();
        }

        // 2. Sort account numbers for consistent lock ordering (deadlock prevention)
        String firstAccountNumber = sourceAccountNumber.compareTo(targetAccountNumber) < 0
                ? sourceAccountNumber : targetAccountNumber;
        String secondAccountNumber = sourceAccountNumber.compareTo(targetAccountNumber) < 0
                ? targetAccountNumber : sourceAccountNumber;

        // 3. Lock accounts in sorted order
        Account first = accountRepository.findByAccountNumberWithLock(firstAccountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + firstAccountNumber));
        Account second = accountRepository.findByAccountNumberWithLock(secondAccountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + secondAccountNumber));

        // 4. Map back to source/target
        Account source = sourceAccountNumber.equals(firstAccountNumber) ? first : second;
        Account target = targetAccountNumber.equals(firstAccountNumber) ? first : second;

        // 5. Validate
        if (source.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: account={}, balance={}, requested={}",
                    sourceAccountNumber, source.getBalance(), amount);
            throw new InsufficientBalanceException("Insufficient balance");
        }

        // 6. Execute transfer
        BigDecimal sourceOldBalance = source.getBalance();
        BigDecimal targetOldBalance = target.getBalance();

        source.setBalance(source.getBalance().subtract(amount));
        target.setBalance(target.getBalance().add(amount));

        accountRepository.save(source);
        accountRepository.save(target);

        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccount(source)
                .targetAccount(target)
                .amount(amount)
                .type(Transaction.Type.TRANSFER)
                .status(TransferStatus.COMPLETED)
                .description(description)
                .build();
        tx = transactionRepository.save(tx);

        ledgerService.createLedger(tx, source, target, amount);

        outboxService.publishEvent("TRANSACTION", tx.getId(), "TransferCompleted",
                String.format("{\"transactionId\":%d,\"source\":\"%s\",\"target\":\"%s\",\"amount\":%s}",
                        tx.getId(), sourceAccountNumber, targetAccountNumber, amount));

        auditService.log("TRANSFER",
                "Transferred " + amount + " from " + sourceAccountNumber + " to " + targetAccountNumber,
                source.getUser());

        log.info("Transfer completed: source={} ({} -> {}), target={} ({} -> {}), amount={}, txId={}",
                sourceAccountNumber, sourceOldBalance, source.getBalance(),
                targetAccountNumber, targetOldBalance, target.getBalance(), amount, tx.getId());
        return tx;
    }
}
