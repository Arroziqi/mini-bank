package com.bank.core.controller;

import com.bank.core.dto.AccountDto;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;

    @GetMapping("/my")
    public ResponseEntity<List<AccountDto>> getMyAccounts(@AuthenticationPrincipal User user) {
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
}
