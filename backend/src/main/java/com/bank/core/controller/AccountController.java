package com.bank.core.controller;

import com.bank.core.dto.AccountDto;
import com.bank.core.exception.ResourceNotFoundException;
import com.bank.core.model.Account;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountRepository accountRepository;

    @GetMapping("/my")
    public ResponseEntity<List<AccountDto>> getMyAccounts(@AuthenticationPrincipal User user) {
        log.debug("Fetching accounts for user: {}", user.getUsername());
        List<AccountDto> accounts = accountRepository.findByUserId(user.getId())
                .stream()
                .map(acc -> AccountDto.builder()
                        .id(acc.getId())
                        .accountNumber(acc.getAccountNumber())
                        .balance(acc.getBalance())
                        .status(acc.getStatus())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/lookup/{accountNumber}")
    public ResponseEntity<AccountDto> lookupAccount(@PathVariable String accountNumber) {
        log.debug("Account lookup request: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account lookup failed: {}", accountNumber);
                    return new ResourceNotFoundException("Account not found");
                });

        String holderName = account.getUser().getUsername();

        return ResponseEntity.ok(AccountDto.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .holderName(holderName)
                .balance(account.getBalance())
                .status(account.getStatus())
                .build());
    }
}
