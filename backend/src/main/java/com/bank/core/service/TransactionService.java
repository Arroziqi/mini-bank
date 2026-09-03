package com.bank.core.service;

import com.bank.core.exception.InsufficientBalanceException;
import com.bank.core.exception.ResourceNotFoundException;
import com.bank.core.model.Account;
import com.bank.core.model.Transaction;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionService {
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        private final AuditService auditService;

        private static final int MAX_RETRIES = 3;
        private static final long RETRY_DELAY_MS = 100;

        @Transactional
        public void deposit(String accountNumber, BigDecimal amount) {
                for (int i = 0; i < MAX_RETRIES; i++) {
                        try {
                                Account account = accountRepository.findByAccountNumber(accountNumber)
                                                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

                                account.setBalance(account.getBalance().add(amount));
                                accountRepository.save(account);

                                Transaction tx = Transaction.builder()
                                                .targetAccount(account)
                                                .amount(amount)
                                                .type(Transaction.Type.DEPOSIT)
                                                .build();
                                transactionRepository.save(tx);

                                auditService.log("DEPOSIT", "Deposited " + amount + " to " + accountNumber,
                                                account.getUser());
                                return;
                        } catch (ObjectOptimisticLockingFailureException ex) {
                                handleOptimisticLockRetry(i);
                        }
                }
        }

        @Transactional
        public void withdraw(String accountNumber, BigDecimal amount) {
                for (int i = 0; i < MAX_RETRIES; i++) {
                        try {
                                Account account = accountRepository.findByAccountNumber(accountNumber)
                                                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

                                if (account.getBalance().compareTo(amount) < 0) {
                                        throw new InsufficientBalanceException("Insufficient balance");
                                }

                                account.setBalance(account.getBalance().subtract(amount));
                                accountRepository.save(account);

                                Transaction tx = Transaction.builder()
                                                .sourceAccount(account)
                                                .amount(amount)
                                                .type(Transaction.Type.WITHDRAWAL)
                                                .build();
                                transactionRepository.save(tx);

                                auditService.log("WITHDRAWAL", "Withdrew " + amount + " from " + accountNumber,
                                                account.getUser());
                                return;
                        } catch (ObjectOptimisticLockingFailureException ex) {
                                handleOptimisticLockRetry(i);
                        }
                }
        }

        @Transactional
        public void transfer(String sourceAccountNumber, String targetAccountNumber, BigDecimal amount) {
                for (int i = 0; i < MAX_RETRIES; i++) {
                        try {
                                Account source = accountRepository.findByAccountNumber(sourceAccountNumber)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Source account not found"));
                                Account target = accountRepository.findByAccountNumber(targetAccountNumber)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Target account not found"));

                                if (source.getBalance().compareTo(amount) < 0) {
                                        throw new InsufficientBalanceException("Insufficient balance");
                                }

                                source.setBalance(source.getBalance().subtract(amount));
                                target.setBalance(target.getBalance().add(amount));

                                accountRepository.save(source);
                                accountRepository.save(target);

                                Transaction tx = Transaction.builder()
                                                .sourceAccount(source)
                                                .targetAccount(target)
                                                .amount(amount)
                                                .type(Transaction.Type.TRANSFER)
                                                .build();
                                transactionRepository.save(tx);

                                auditService.log("TRANSFER",
                                                "Transferred " + amount + " from " + sourceAccountNumber + " to "
                                                                + targetAccountNumber,
                                                source.getUser());
                                return;
                        } catch (ObjectOptimisticLockingFailureException ex) {
                                handleOptimisticLockRetry(i);
                        }
                }
        }

        private void handleOptimisticLockRetry(int attempt) {
                if (attempt == MAX_RETRIES - 1) {
                        throw new RuntimeException(
                                        "Operation failed after " + MAX_RETRIES
                                                        + " retries due to concurrent modification");
                }
                try {
                        Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                }
        }
}
