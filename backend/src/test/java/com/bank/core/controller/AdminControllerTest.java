package com.bank.core.controller;

import com.bank.core.dto.AuthDto;
import com.bank.core.model.Account;
import com.bank.core.model.AuditLog;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.AuditLogRepository;
import com.bank.core.repository.UserRepository;
import com.bank.core.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .email("admin@example.com")
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);

        User customer = User.builder()
                .username("customer")
                .password(passwordEncoder.encode("customer123"))
                .email("customer@example.com")
                .role(User.Role.CUSTOMER)
                .build();
        userRepository.save(customer);

        Account customerAccount = Account.builder()
                .accountNumber("ACC-123456")
                .balance(new BigDecimal("1000.00"))
                .status(Account.Status.ACTIVE)
                .user(customer)
                .build();
        accountRepository.save(customerAccount);

        AuditLog auditLog = AuditLog.builder()
                .action("TEST_ACTION")
                .details("Test audit log entry")
                .user(customer)
                .build();
        auditLogRepository.save(auditLog);

        AuthDto.LoginRequest adminLogin = AuthDto.LoginRequest.builder()
                .username("admin")
                .password("admin123")
                .build();
        var adminResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andReturn();
        var adminBody = objectMapper.readTree(adminResponse.getResponse().getContentAsString());
        adminToken = "Bearer " + adminBody.get("token").asText();

        AuthDto.LoginRequest customerLogin = AuthDto.LoginRequest.builder()
                .username("customer")
                .password("customer123")
                .build();
        var customerResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerLogin)))
                .andReturn();
        var customerBody = objectMapper.readTree(customerResponse.getResponse().getContentAsString());
        customerToken = "Bearer " + customerBody.get("token").asText();
    }

    @Test
    void adminUsers_shouldReturnAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void adminAuditLogs_shouldReturnAllLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void adminEndpoints_shouldRejectCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }
}
