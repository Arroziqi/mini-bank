package com.bank.core.controller;

import com.bank.core.dto.AuthDto;
import com.bank.core.model.Account;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.UserRepository;
import com.bank.core.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private String userToken;
    private Account userAccount;

    @BeforeEach
    void setUp() throws Exception {
        User user = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode("password123"))
                .email("test@example.com")
                .role(User.Role.CUSTOMER)
                .build();
        userRepository.save(user);

        userAccount = Account.builder()
                .accountNumber("ACC-123456")
                .balance(new BigDecimal("1000.00"))
                .status(Account.Status.ACTIVE)
                .user(user)
                .build();
        accountRepository.save(userAccount);

        AuthDto.LoginRequest loginRequest = AuthDto.LoginRequest.builder()
                .username("testuser")
                .password("password123")
                .build();
        var response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();
        var responseBody = objectMapper.readTree(response.getResponse().getContentAsString());
        userToken = "Bearer " + responseBody.get("token").asText();
    }

    @Test
    void deposit_shouldIncreaseBalance() throws Exception {
        String requestJson = String.format(
                "{\"sourceAccountNumber\":\"%s\",\"amount\":500}", userAccount.getAccountNumber());

        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Deposit successful"));
    }

    @Test
    void withdraw_shouldDecreaseBalance() throws Exception {
        String requestJson = String.format(
                "{\"sourceAccountNumber\":\"%s\",\"amount\":300}", userAccount.getAccountNumber());

        mockMvc.perform(post("/api/v1/transactions/withdraw")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Withdrawal successful"));
    }

    @Test
    void withdraw_shouldReturn400ForInsufficientBalance() throws Exception {
        String requestJson = String.format(
                "{\"sourceAccountNumber\":\"%s\",\"amount\":5000}", userAccount.getAccountNumber());

        mockMvc.perform(post("/api/v1/transactions/withdraw")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_shouldMoveFunds() throws Exception {
        User targetUser = User.builder()
                .username("targetuser")
                .password(passwordEncoder.encode("password123"))
                .email("target@example.com")
                .role(User.Role.CUSTOMER)
                .build();
        userRepository.save(targetUser);

        Account targetAccount = Account.builder()
                .accountNumber("ACC-789012")
                .balance(new BigDecimal("500.00"))
                .status(Account.Status.ACTIVE)
                .user(targetUser)
                .build();
        accountRepository.save(targetAccount);

        String requestJson = String.format(
                "{\"sourceAccountNumber\":\"%s\",\"targetAccountNumber\":\"ACC-789012\",\"amount\":200}",
                userAccount.getAccountNumber());

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Transfer successful"));
    }

    @Test
    void history_shouldReturnPaginatedResults() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/history/" + userAccount.getAccountNumber())
                        .header("Authorization", userToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void endpoints_shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountNumber\":\"ACC-123456\",\"amount\":100}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/transactions/history/ACC-123456"))
                .andExpect(status().isUnauthorized());
    }
}
