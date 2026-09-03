# MVP Finalization Plan - Mini Core Banking System

> **Purpose:** Task list & patokan untuk AI agent mengerjakan finalisasi MVP.
> **Status:** Dalam pengerjaan
> **Total Tasks:** 28 tasks across 5 phases

---

## Phase 1: Bug Fixes & Quick Wins

### 1.1 Fix Bug Login.vue - Loading State

- **File:** `frontend/src/views/Login.vue:15`
- **Issue:** `loading.ref = true` seharusnya `loading.value = true`
- **Impact:** Loading state tidak berfungsi, tombol login tidak disable saat proses
- **Action:**
  ```
  Ubah baris 15 dari: loading.ref = true;
  Menjadi:            loading.value = true;
  ```
- **Verification:** Login process harus disable tombol "Sign In" dan tampilkan "Logging in..."

### 1.2 Hapus Unused Component HelloWorld.vue

- **File:** `frontend/src/components/HelloWorld.vue`
- **Issue:** Component boilerplate Vite yang tidak digunakan
- **Action:** Hapus file `HelloWorld.vue`
- **Verification:** Tidak ada import error di manapun

---

## Phase 2: Backend Hardening

### 2.1 Custom Exception Classes

- **Tujuan:** Ganti generic `RuntimeException` dengan custom exceptions yang lebih descriptive
- **Files to create:**
  - `backend/src/main/java/com/bank/core/exception/ResourceNotFoundException.java`
  - `backend/src/main/java/com/bank/core/exception/InsufficientBalanceException.java`
  - `backend/src/main/java/com/bank/core/exception/DuplicateResourceException.java`
- **Implementation:**
  ```java
  // ResourceNotFoundException.java
  public class ResourceNotFoundException extends RuntimeException {
      public ResourceNotFoundException(String message) { super(message); }
  }

  // InsufficientBalanceException.java
  public class InsufficientBalanceException extends RuntimeException {
      public InsufficientBalanceException(String message) { super(message); }
  }

  // DuplicateResourceException.java
  public class DuplicateResourceException extends RuntimeException {
      public DuplicateResourceException(String message) { super(message); }
  }
  ```

### 2.2 Update GlobalExceptionHandler

- **File:** `backend/src/main/java/com/bank/core/exception/GlobalExceptionHandler.java`
- **Action:** Tambahkan handler untuk custom exceptions dengan HTTP status yang tepat
- **Implementation:**
  ```java
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex) {
      Map<String, Object> body = new HashMap<>();
      body.put("timestamp", LocalDateTime.now());
      body.put("message", ex.getMessage());
      body.put("status", HttpStatus.NOT_FOUND.value());
      return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(InsufficientBalanceException.class)
  public ResponseEntity<Object> handleInsufficientBalance(InsufficientBalanceException ex) {
      Map<String, Object> body = new HashMap<>();
      body.put("timestamp", LocalDateTime.now());
      body.put("message", ex.getMessage());
      body.put("status", HttpStatus.BAD_REQUEST.value());
      return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<Object> handleDuplicateResource(DuplicateResourceException ex) {
      Map<String, Object> body = new HashMap<>();
      body.put("timestamp", LocalDateTime.now());
      body.put("message", ex.getMessage());
      body.put("status", HttpStatus.CONFLICT.value());
      return new ResponseEntity<>(body, HttpStatus.CONFLICT);
  }
  ```

### 2.3 Update Services dengan Custom Exceptions

- **Files:**
  - `backend/src/main/java/com/bank/core/service/TransactionService.java`
  - `backend/src/main/java/com/bank/core/service/AccountService.java`
  - `backend/src/main/java/com/bank/core/controller/AuthController.java`
- **Changes:**
  - `TransactionService.java`:
    - Line 23: `RuntimeException("Account not found")` -> `ResourceNotFoundException("Account not found")`
    - Line 44: `RuntimeException("Insufficient balance")` -> `InsufficientBalanceException("Insufficient balance")`
    - Line 68: `RuntimeException("Insufficient balance")` -> `InsufficientBalanceException("Insufficient balance")`
  - `AuthController.java`:
    - Line 41: `"Username is already taken!"` -> throw `DuplicateResourceException("Username is already taken!")`

### 2.4 Optimistic Lock Retry Mechanism

- **File:** `backend/src/main/java/com/bank/core/service/TransactionService.java`
- **Issue:** `@Version` ada tapi tidak ada retry mechanism untuk handle `OptimisticLockException`
- **Action:** Tambahkan retry logic pada method `transfer()`
- **Implementation:**
  ```java
  @Transactional
  public void transfer(String sourceAccountNumber, String targetAccountNumber, BigDecimal amount) {
      int maxRetries = 3;
      for (int i = 0; i < maxRetries; i++) {
          try {
              Account source = accountRepository.findByAccountNumber(sourceAccountNumber)
                      .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
              Account target = accountRepository.findByAccountNumber(targetAccountNumber)
                      .orElseThrow(() -> new ResourceNotFoundException("Target account not found"));

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
                      "Transferred " + amount + " from " + sourceAccountNumber + " to " + targetAccountNumber,
                      source.getUser());
              return; // Success, exit
          } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
              if (i == maxRetries - 1) {
                  throw new RuntimeException("Transfer failed after " + maxRetries + " retries due to concurrent modification");
              }
              // Wait briefly before retry
              try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
          }
      }
  }
  ```
- **Note:** Apply retry pattern juga ke `deposit()` dan `withdraw()`

### 2.5 Input Validation dengan Jakarta Validation

- **Files to modify:**
  - `backend/src/main/java/com/bank/core/dto/AuthDto.java`
  - `backend/src/main/java/com/bank/core/dto/TransactionDto.java`
  - `backend/src/main/java/com/bank/core/controller/TransactionController.java`
  - `backend/src/main/java/com/bank/core/controller/AuthController.java`
- **Implementation:**

  **AuthDto.java:**
  ```java
  public class AuthDto {
      @Data
      public static class LoginRequest {
          @NotBlank(message = "Username is required")
          private String username;

          @NotBlank(message = "Password is required")
          private String password;
      }

      @Data
      public static class RegisterRequest {
          @NotBlank(message = "Username is required")
          @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
          private String username;

          @NotBlank(message = "Password is required")
          @Size(min = 6, message = "Password must be at least 6 characters")
          private String password;

          @NotBlank(message = "Email is required")
          @Email(message = "Email must be valid")
          private String email;
      }
      // ... JwtResponse unchanged
  }
  ```

  **TransactionDto.java:**
  ```java
  public class TransactionDto {
      @Data
      public static class Request {
          @NotBlank(message = "Source account number is required")
          private String sourceAccountNumber;

          private String targetAccountNumber; // Optional for deposit

          @NotNull(message = "Amount is required")
          @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
          private BigDecimal amount;
      }
      // ... Response unchanged
  }
  ```

  **Controllers:** Tambahkan `@Valid` pada `@RequestBody` parameters

### 2.6 Redis Token Blacklist (Force Logout)

- **Goal:** Implement token blacklisting agar logout benar-benar invalidate JWT
- **Files to create/modify:**
  - `backend/src/main/java/com/bank/core/service/TokenBlacklistService.java` (NEW)
  - `backend/src/main/java/com/bank/core/security/JwtAuthenticationFilter.java` (MODIFY)
  - `backend/src/main/java/com/bank/core/controller/AuthController.java` (MODIFY - tambah logout endpoint)

  **TokenBlacklistService.java (NEW):**
  ```java
  @Service
  @RequiredArgsConstructor
  public class TokenBlacklistService {
      private final RedisTemplate<String, String> redisTemplate;
      @Value("${app.jwt.expiration-ms}")
      private long jwtExpirationInMs;

      public void blacklist(String token) {
          redisTemplate.opsForValue().set("blacklist:" + token, "true", jwtExpirationInMs, TimeUnit.MILLISECONDS);
      }

      public boolean isBlacklisted(String token) {
          return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
      }
  }
  ```

  **JwtAuthenticationFilter.java (MODIFY):** Tambahkan pengecekan blacklist
  ```java
  // Setelah validateToken returns true:
  if (tokenBlacklistService.isBlacklisted(token)) {
      return; // Token is blacklisted, don't authenticate
  }
  ```

  **AuthController.java (MODIFY):** Tambahkan logout endpoint
  ```java
  @PostMapping("/logout")
  public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
      String token = authHeader.substring(7); // Remove "Bearer " prefix
      tokenBlacklistService.blacklist(token);
      return ResponseEntity.ok("Logged out successfully");
  }
  ```

  **Redis Config (NEW):** `backend/src/main/java/com/bank/core/config/RedisConfig.java`
  ```java
  @Configuration
  public class RedisConfig {
      @Bean
      public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
          RedisTemplate<String, String> template = new RedisTemplate<>();
          template.setConnectionFactory(connectionFactory);
          template.setKeySerializer(new StringRedisSerializer());
          template.setValueSerializer(new StringRedisSerializer());
          return template;
      }
  }
  ```

---

## Phase 3: Backend Testing

### 3.1 Setup Test Infrastructure

- **Files:**
  - `backend/src/test/java/com/bank/core/` - Buat directory structure
  - `backend/src/test/resources/application-test.yml` - Test config
- **Dependencies needed (pom.xml):**
  - H2 Database untuk in-memory testing
  - `spring-boot-starter-test` sudah ada
- **application-test.yml:**
  ```yaml
  spring:
    datasource:
      url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
      driver-class-name: org.h2.Driver
      username: sa
      password:
    jpa:
      hibernate:
        ddl-auto: create-drop
    data:
      redis:
        host: localhost
        port: 6379
  app:
    jwt:
      secret: testSecretKey123456789012345678901234567890
      expiration-ms: 86400000
  ```

### 3.2 Unit Tests - Services

- **Files to create:**
  - `backend/src/test/java/com/bank/core/service/TransactionServiceTest.java`
  - `backend/src/test/java/com/bank/core/service/AccountServiceTest.java`
  - `backend/src/test/java/com/bank/core/service/AuditServiceTest.java`

- **TransactionServiceTest.java:**
  ```java
  @ExtendWith(MockitoExtension.class)
  class TransactionServiceTest {

      @Mock private AccountRepository accountRepository;
      @Mock private TransactionRepository transactionRepository;
      @Mock private AuditService auditService;
      @InjectMocks private TransactionService transactionService;

      @Test
      void deposit_shouldIncreaseBalance() {
          // Setup: Create account with balance 1000, deposit 500
          // Verify: Balance becomes 1500, transaction recorded, audit logged
      }

      @Test
      void deposit_shouldThrowWhenAccountNotFound() {
          // Verify: ResourceNotFoundException thrown
      }

      @Test
      void withdraw_shouldDecreaseBalance() {
          // Setup: Account balance 1000, withdraw 300
          // Verify: Balance becomes 700
      }

      @Test
      void withdraw_shouldThrowWhenInsufficientBalance() {
          // Setup: Account balance 100, withdraw 500
          // Verify: InsufficientBalanceException thrown
      }

      @Test
      void transfer_shouldMoveFundsBetweenAccounts() {
          // Setup: Source 1000, Target 500, transfer 200
          // Verify: Source becomes 800, Target becomes 700
      }

      @Test
      void transfer_shouldThrowWhenSourceInsufficient() {
          // Verify: InsufficientBalanceException for source
      }
  }
  ```

### 3.3 Unit Tests - Security

- **File to create:** `backend/src/test/java/com/bank/core/security/JwtTokenProviderTest.java`
- **Tests:**
  ```java
  @ExtendWith(MockitoExtension.class)
  class JwtTokenProviderTest {
      // Test: generateToken returns non-null token
      // Test: getUsernameFromJWT extracts correct username
      // Test: validateToken returns true for valid token
      // Test: validateToken returns false for expired token
      // Test: validateToken returns false for tampered token
  }
  ```

### 3.4 Integration Tests - API Endpoints

- **Files to create:**
  - `backend/src/test/java/com/bank/core/controller/AuthControllerTest.java`
  - `backend/src/test/java/com/bank/core/controller/TransactionControllerTest.java`
  - `backend/src/test/java/com/bank/core/controller/AccountControllerTest.java`
  - `backend/src/test/java/com/bank/core/controller/AdminControllerTest.java`

- **AuthControllerTest.java:**
  ```java
  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  class AuthControllerTest {

      @Autowired private MockMvc mockMvc;
      @Autowired private ObjectMapper objectMapper;

      @Test
      void login_shouldReturnJwtToken() throws Exception {
          // POST /api/v1/auth/login with valid credentials
          // Verify: 200 OK, token returned
      }

      @Test
      void login_shouldReturn401ForInvalidCredentials() throws Exception {
          // POST /api/v1/auth/login with wrong password
          // Verify: 401 Unauthorized
      }

      @Test
      void register_shouldCreateNewUser() throws Exception {
          // POST /api/v1/auth/register with valid data
          // Verify: 200 OK, user created
      }

      @Test
      void register_shouldReturn400ForDuplicateUsername() throws Exception {
          // Register same username twice
          // Verify: 400 Bad Request
      }
  }
  ```

- **TransactionControllerTest.java:**
  ```java
  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  class TransactionControllerTest {

      // Setup: Create test user and account via repository

      @Test
      void deposit_shouldIncreaseBalance() throws Exception {
          // POST /api/v1/transactions/deposit with JWT
          // Verify: 200 OK
      }

      @Test
      void transfer_shouldMoveFunds() throws Exception {
          // POST /api/v1/transactions/transfer with JWT
          // Verify: 200 OK, both accounts updated
      }

      @Test
      void transfer_shouldReturn400ForInsufficientBalance() throws Exception {
          // Transfer more than available
          // Verify: 400 Bad Request
      }

      @Test
      void history_shouldReturnPaginatedResults() throws Exception {
          // GET /api/v1/transactions/history/{accountNumber}
          // Verify: 200 OK, pagination structure
      }

      @Test
      void endpoints_shouldReturn401WithoutToken() throws Exception {
          // All transaction endpoints without JWT
          // Verify: 401 Unauthorized
      }
  }
  ```

### 3.5 Integration Tests - Security & Authorization

- **File to create:** `backend/src/test/java/com/bank/core/security/SecurityIntegrationTest.java`
- **Tests:**
  ```java
  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  class SecurityIntegrationTest {

      @Test
      void publicEndpoints_shouldNotRequireAuth() throws Exception {
          // POST /api/v1/auth/login, /api/v1/auth/register
          // Verify: Accessible without JWT
      }

      @Test
      void protectedEndpoints_shouldRequireAuth() throws Exception {
          // GET /api/v1/accounts/my, POST /api/v1/transactions/*
          // Verify: 401 without JWT
      }

      @Test
      void adminEndpoints_shouldRejectCustomerRole() throws Exception {
          // GET /api/v1/admin/users with CUSTOMER JWT
          // Verify: 403 Forbidden
      }

      @Test
      void adminEndpoints_shouldAllowAdminRole() throws Exception {
          // GET /api/v1/admin/users with ADMIN JWT
          // Verify: 200 OK
      }
  }
  ```

---

## Phase 4: Frontend Enhancements

### 4.1 Frontend Testing Setup

- **Dependencies to add (package.json):**
  ```json
  "devDependencies": {
    "vitest": "^2.0.0",
    "@vue/test-utils": "^2.4.0",
    "jsdom": "^24.0.0"
  }
  ```
- **Config:** Tambahkan test config di `vite.config.js`
  ```javascript
  /// <reference types="vitest" />
  export default defineConfig({
    // ... existing config
    test: {
      environment: 'jsdom',
      globals: true,
    },
  })
  ```
- **Update package.json scripts:**
  ```json
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest",
    "test:coverage": "vitest run --coverage"
  }
  ```

### 4.2 Frontend Unit Tests - Stores

- **Files to create:**
  - `frontend/src/stores/__tests__/auth.spec.js`
  - `frontend/src/stores/__tests__/account.spec.js`

- **auth.spec.js:**
  ```javascript
  import { describe, it, expect, beforeEach, vi } from 'vitest';
  import { setActivePinia, createPinia } from 'pinia';
  import { useAuthStore } from '../auth';
  import api from '../../api';

  vi.mock('../../api');

  describe('Auth Store', () => {
      beforeEach(() => {
          setActivePinia(createPinia());
          localStorage.clear();
      });

      it('should start unauthenticated', () => {
          const auth = useAuthStore();
          expect(auth.isAuthenticated).toBe(false);
          expect(auth.isAdmin).toBe(false);
      });

      it('login should set token and user', async () => {
          api.post.mockResolvedValue({ data: { token: 'jwt-123', username: 'user1', role: 'CUSTOMER' } });
          const auth = useAuthStore();
          await auth.login('user1', 'pass123');
          expect(auth.isAuthenticated).toBe(true);
          expect(auth.token).toBe('jwt-123');
      });

      it('logout should clear state', async () => {
          const auth = useAuthStore();
          auth.token = 'jwt-123';
          auth.user = { username: 'user1', role: 'CUSTOMER' };
          auth.logout();
          expect(auth.isAuthenticated).toBe(false);
          expect(auth.token).toBeNull();
      });
  });
  ```

### 4.3 Frontend Unit Tests - Components

- **Files to create:**
  - `frontend/src/views/__tests__/Login.spec.js`
  - `frontend/src/views/__tests__/Register.spec.js`
  - `frontend/src/views/__tests__/Transfer.spec.js`

- **Login.spec.js:**
  ```javascript
  import { describe, it, expect, vi, beforeEach } from 'vitest';
  import { mount } from '@vue/test-utils';
  import { createPinia, setActivePinia } from 'pinia';
  import Login from '../Login.vue';
  import router from '../../router';

  describe('Login.vue', () => {
      beforeEach(() => {
          setActivePinia(createPinia());
      });

      it('renders login form', () => {
          const wrapper = mount(Login, { global: { plugins: [router] } });
          expect(wrapper.find('h2').text()).toBe('Welcome Back');
          expect(wrapper.find('input[type="text"]').exists()).toBe(true);
          expect(wrapper.find('input[type="password"]').exists()).toBe(true);
      });

      it('displays error on failed login', async () => {
          const wrapper = mount(Login, { global: { plugins: [router] } });
          // Simulate failed login
          // Check error message displayed
      });

      it('disables button during loading', async () => {
          const wrapper = mount(Login, { global: { plugins: [router] } });
          // Check button disabled state during async operation
      });
  });
  ```

### 4.4 Frontend E2E Tests (Optional - Cypress/Playwright)

- **Note:** Ini optional, bisa ditambahkan nanti
- **Jika ditambahkan:**
  - Install Cypress atau Playwright
  - Buat test scenarios:
    1. Login -> Dashboard -> Logout
    2. Register -> Login -> Dashboard
    3. Transfer funds between accounts
    4. Admin view users and audit logs

---

## Phase 5: CI/CD & Deployment Polish

### 5.1 Update GitLab CI dengan Test Stage

- **File:** `.gitlab-ci.yml`
- **Issue:** Test stage ada tapi tidak ada test files
- **Action:** Setelah Phase 3 selesai, pastikan test stage berjalan
- **Update:** Tambahkan frontend test stage
  ```yaml
  test:frontend:
    stage: test
    image: node:20-alpine
    needs:
      - build:frontend
    script:
      - cd frontend
      - npm ci
      - npm run test
    rules:
      - if: $CI_PIPELINE_SOURCE == "merge_request_event"
      - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
  ```

### 5.2 Docker Healthchecks

- **File:** `docker-compose.yml`
- **Action:** Tambahkan healthcheck untuk backend dan frontend
  ```yaml
  app-backend:
    # ... existing config
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/swagger-ui/index.html"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  app-frontend:
    # ... existing config
    depends_on:
      app-backend:
        condition: service_healthy
  ```

### 5.3 Update README.md

- **File:** `README.md`
- **Action:** Update bagian "Future Improvements" dan tambahkan test coverage info
- **Tambahkan:**
  - Badge test status
  - Test coverage report info
  - Updated feature list

---

## Execution Order (Dependency Graph)

```
Phase 1 (Quick Wins)
    ├── 1.1 Fix Login Bug ─────────────────────────┐
    └── 1.2 Delete HelloWorld ──────────────────────┤
                                                     │
Phase 2 (Backend Hardening) ────────────────────────┤
    ├── 2.1 Custom Exceptions ──┐                    │
    ├── 2.2 Update Handler ─────┼──→ 2.3 Update Services
    ├── 2.4 Optimistic Lock ────┘                    │
    ├── 2.5 Input Validation ────────────────────────┤
    └── 2.6 Redis Token Blacklist ───────────────────┤
                                                     │
Phase 3 (Backend Testing) ──────────────────────────┤
    ├── 3.1 Test Infrastructure ─┐                   │
    ├── 3.2 Service Tests ───────┼──→ 3.4 API Tests  │
    ├── 3.3 Security Tests ──────┘                   │
    └── 3.5 Security Integration ────────────────────┤
                                                     │
Phase 4 (Frontend Enhancements) ────────────────────┤
    ├── 4.1 Frontend Test Setup ─┐                   │
    ├── 4.2 Store Tests ─────────┼──→ 4.3 Component Tests
    └── 4.4 E2E Tests (Optional) ┘                   │
                                                     │
Phase 5 (CI/CD & Polish) ───────────────────────────┘
    ├── 5.1 Update GitLab CI
    ├── 5.2 Docker Healthchecks
    └── 5.3 Update README
```

---

## Checklist

### Phase 1: Bug Fixes & Quick Wins
- [ ] 1.1 Fix Login.vue loading bug
- [ ] 1.2 Delete HelloWorld.vue

### Phase 2: Backend Hardening
- [ ] 2.1 Create custom exception classes
- [ ] 2.2 Update GlobalExceptionHandler
- [ ] 2.3 Update services to use custom exceptions
- [ ] 2.4 Add Optimistic Lock retry mechanism
- [ ] 2.5 Add Jakarta Validation to DTOs
- [ ] 2.6 Implement Redis Token Blacklist

### Phase 3: Backend Testing
- [ ] 3.1 Setup test infrastructure (H2, test config)
- [ ] 3.2 Write TransactionService unit tests
- [ ] 3.3 Write JwtTokenProvider unit tests
- [ ] 3.4 Write API integration tests (Auth, Transaction, Account, Admin)
- [ ] 3.5 Write Security integration tests

### Phase 4: Frontend Enhancements
- [ ] 4.1 Setup frontend testing (Vitest + Vue Test Utils)
- [ ] 4.2 Write store unit tests (auth, account)
- [ ] 4.3 Write component unit tests (Login, Register, Transfer)
- [ ] 4.4 (Optional) Write E2E tests

### Phase 5: CI/CD & Deployment Polish
- [ ] 5.1 Add frontend test stage to GitLab CI
- [ ] 5.2 Add Docker healthchecks
- [ ] 5.3 Update README.md

---

## Notes for AI Agent

1. **Selalu run lint/typecheck setelah edit:**
   - Backend: `cd backend && ./mvnw compile`
   - Frontend: `cd frontend && npm run build`

2. **Testing commands:**
   - Backend: `cd backend && ./mvnw test`
   - Frontend: `cd frontend && npm run test`

3. **Code conventions:**
   - Backend: Java 17, Spring Boot 3.2.4, Lombok, Layered architecture
   - Frontend: Vue 3 Composition API (`<script setup>`), Pinia, Axios

4. **Import existing patterns:** Lihat file sebelum menulis code baru

5. **Commit strategy:** Jangan commit kecuali user minta

6. **File paths reference:**
   - Backend: `backend/src/main/java/com/bank/core/`
   - Frontend: `frontend/src/`
   - Tests: `backend/src/test/java/com/bank/core/`
   - Frontend tests: `frontend/src/**/__tests__/`
