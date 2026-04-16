package com.bank.core.service;

import com.bank.core.model.Account;
import com.bank.core.model.Transaction;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionService {
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        private final AuditService auditService;

        @Transactional
        public void deposit(String accountNumber, BigDecimal amount) {
                Account account = accountRepository.findByAccountNumber(accountNumber)
                                .orElseThrow(() -> new RuntimeException("Account not found"));

                account.setBalance(account.getBalance().add(amount));
                accountRepository.save(account);

                Transaction tx = Transaction.builder()
                                .targetAccount(account)
                                .amount(amount)
                                .type(Transaction.Type.DEPOSIT)
                                .build();
                transactionRepository.save(tx);

                auditService.log("DEPOSIT", "Deposited " + amount + " to " + accountNumber, account.getUser());
        }

        @Transactional
        public void withdraw(String accountNumber, BigDecimal amount) {
                Account account = accountRepository.findByAccountNumber(accountNumber)
                                .orElseThrow(() -> new RuntimeException("Account not found"));

                if (account.getBalance().compareTo(amount) < 0) {
                        throw new RuntimeException("Insufficient balance");
                }

                account.setBalance(account.getBalance().subtract(amount));
                accountRepository.save(account);

                Transaction tx = Transaction.builder()
                                .sourceAccount(account)
                                .amount(amount)
                                .type(Transaction.Type.WITHDRAWAL)
                                .build();
                transactionRepository.save(tx);

                auditService.log("WITHDRAWAL", "Withdrew " + amount + " from " + accountNumber, account.getUser());
        }

        @Transactional
        public void transfer(String sourceAccountNumber, String targetAccountNumber, BigDecimal amount) {
                Account source = accountRepository.findByAccountNumber(sourceAccountNumber)
                                .orElseThrow(() -> new RuntimeException("Source account not found"));
                Account target = accountRepository.findByAccountNumber(targetAccountNumber)
                                .orElseThrow(() -> new RuntimeException("Target account not found"));

                if (source.getBalance().compareTo(amount) < 0) {
                        throw new RuntimeException("Insufficient balance");
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
                                "Transferred " + amount + " from " + sourceAccountNumber + " to " + targetAccountNumber,
                                source.getUser());
        }
}
