package com.bank.core.service;

import com.bank.core.exception.ResourceNotFoundException;
import com.bank.core.model.Account;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @Transactional
    public Account createAccount(User user) {
        Account account = Account.builder()
                .accountNumber(UUID.randomUUID().toString())
                .balance(BigDecimal.ZERO)
                .status(Account.Status.ACTIVE)
                .user(user)
                .build();

        Account savedAccount = accountRepository.save(account);
        auditService.log("ACCOUNT_CREATED", "New account created: " + savedAccount.getAccountNumber(), user);

        log.info("Account created: accountNumber={}, userId={}, username={}",
                savedAccount.getAccountNumber(), user.getId(), user.getUsername());
        return savedAccount;
    }

    public Account getAccount(String accountNumber) {
        log.debug("Looking up account: {}", accountNumber);
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found: {}", accountNumber);
                    return new ResourceNotFoundException("Account not found");
                });
    }
}
