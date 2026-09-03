package com.bank.core.security;

import com.bank.core.dto.AuthDto;
import com.bank.core.model.User;
import com.bank.core.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
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
    }

    private String login(String username, String password) throws Exception {
        AuthDto.LoginRequest request = AuthDto.LoginRequest.builder()
                .username(username)
                .password(password)
                .build();
        var response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        var body = objectMapper.readTree(response.getResponse().getContentAsString());
        return "Bearer " + body.get("token").asText();
    }

    @Test
    void publicEndpoints_shouldNotRequireAuth() throws Exception {
        AuthDto.LoginRequest loginRequest = AuthDto.LoginRequest.builder()
                .username("customer")
                .password("customer123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        AuthDto.RegisterRequest registerRequest = AuthDto.RegisterRequest.builder()
                .username("newuser")
                .password("password123")
                .email("new@example.com")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoints_shouldRequireAuth() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/my"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountNumber\":\"ACC-123\",\"amount\":100}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoints_shouldAllowAuthenticatedUser() throws Exception {
        String token = login("customer", "customer123");

        mockMvc.perform(get("/api/v1/accounts/my")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_shouldRejectCustomerRole() throws Exception {
        String customerToken = login("customer", "customer123");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_shouldAllowAdminRole() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }
}
