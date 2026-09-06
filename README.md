# Mini Core Banking System

A scalable, production-ready Fullstack banking application designed to handle high-concurrency environments securely.

Built with **Java Spring Boot 3** and **Vue.js 3**, it utilizes modern transaction safeguards (Optimistic Locking with Retry) and relies on robust data infrastructure (**PostgreSQL** and **Redis**) to prevent data races, ensuring absolute financial consistency.

---

## Architecture & Tech Stack

### Frontend (Vue.js 3)
- **Framework**: Vue 3 (Composition API / `<script setup>`)
- **State Management**: Pinia
- **Routing**: Vue Router
- **Web Client**: Axios with JWT Interceptors
- **Build Tool**: Vite
- **Testing**: Vitest + Vue Test Utils
- **Deployment**: Nginx Alpine Docker container

### Backend (Java Spring Boot 3)
- **Framework**: Spring Boot 3.2.4 (Java 17)
- **Security**: Spring Security + JWT
- **Persistence Layer**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL 16 (Source of Truth, ACID compliant)
- **Cache**: Redis 7 (Token Blacklist for force-logout)
- **API Documentation**: OpenAPI / Swagger UI
- **Testing**: JUnit 5 + Mockito + H2 (in-memory)
- **Design Pattern**: Layered REST API (Controller -> Service -> Repository)

---

## Features

### Authentication & Authorization
- Robust **JWT-based Authentication** with proper filtering.
- **Role-based Access Control** (`CUSTOMER` vs `ADMIN`).
- **Token Blacklist** via Redis for secure logout/force-logout.

### Account Management
- **Customer Dashboard**: Overview of user accounts and balances.
- Creation of financial entities with strict database constraints.

### Transaction System (Concurrent Safety Layer)
- Supports: **Deposits**, **Withdrawals**, and internal **Transfers**.
- Protected with **`@Transactional`** boundaries.
- **Double-Entry Ledger**: Every transfer creates matching DEBIT/CREDIT entries with balance verification.
- **Pessimistic Locking**: `SELECT FOR UPDATE` with consistent lock ordering to prevent deadlocks.
- **Idempotency**: Idempotency key via header + unique DB constraint prevents double-spending on retries.
- **State Machine**: Transfer status tracking (`INITIATED` -> `COMPLETED` / `FAILED`).
- **Transaction History**: Paginated with date range filtering.

### Input Validation
- **Jakarta Validation** on all request DTOs.
- Custom exceptions with proper HTTP status codes (404, 400, 409).

### Admin & Auditing Portal
- Advanced admin view for user and account monitoring.
- **Audit Logging System**: Separated transaction logging with `REQUIRES_NEW` propagation.

---

## API Documentation

If the server is running, you can access the OpenAPI (Swagger) UI at:
`http://localhost:8080/swagger-ui/index.html`

### Authentication
- `POST /api/v1/auth/login` - Authenticate and get JWT.
- `POST /api/v1/auth/register` - Create customer account.
- `POST /api/v1/auth/logout` - Invalidate JWT token.

### Accounts
- `GET /api/v1/accounts/my` - Get current user's accounts.

### Transactions
- `POST /api/v1/transactions/deposit` - Add to balance.
- `POST /api/v1/transactions/withdraw` - Remove from balance.
- `POST /api/v1/transactions/transfer` - Send balance securely.
- `GET /api/v1/transactions/history/{accountNumber}` - Paginated ledger logs.

### Administration
- `GET /api/v1/admin/users` - Fetches all users (Requires `ROLE_ADMIN`).
- `GET /api/v1/admin/audit-logs` - Inspects internal audit activities.

---

## Setup Instructions (Local Development)

### Prerequisites
- Docker & Docker Compose
- *Optionally for native runs:* JDK 17, Node 18+, Postgres 16 instance.

### Option 1: Docker Deployment
```bash
docker-compose up --build -d
```
- Frontend: `http://localhost:80`
- Backend API: `http://localhost:8080`

### Option 2: Run Natively (Development)
```bash
docker-compose up postgres redis -d
```

**Run Backend:**
```bash
cd backend
./mvnw spring-boot:run
```

**Run Frontend:**
```bash
cd frontend
npm install
npm run dev
```

---

## Testing

### Backend Tests (JUnit 5 + Mockito + H2)
```bash
cd backend
./mvnw test
```
- **Unit Tests**: TransactionService, JwtTokenProvider (Mockito)
- **Integration Tests**: Auth, Transaction, Admin controllers (MockMvc + H2)
- **Security Tests**: Authorization rules, JWT filtering

### Frontend Tests (Vitest + Vue Test Utils)
```bash
cd frontend
npm run test:run
```
- **Store Tests**: Auth store, Account store
- **Component Tests**: Login, Register views

### E2E Tests
```bash
cd e2e-tests
node run-tests.js
```
- 14 test scenarios with 38 assertions covering auth, transactions, and admin flows.

---

## Environment Variables

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=banking_db
DB_USER=postgres
DB_PASS=postgres

REDIS_HOST=localhost
REDIS_PORT=6379

JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_EXPIRATION_MS=86400000

APP_SECURITY_AUTH_WHITELIST="/api/v1/auth/**,/public/**"
```

---

## Security Considerations
- **JWT Authorization**: Stateless and horizontally-scalable security.
- **Token Blacklist**: Redis-backed logout with automatic expiration.
- **Hashing/Salting**: BCryptPasswordEncoder for all passwords.
- **Race Condition Prevention**: Pessimistic locking (`SELECT FOR UPDATE`) with consistent lock ordering.
- **Idempotency**: Prevents double-spending on retry requests.
- **Double-Entry Ledger**: Every transfer creates verifiable DEBIT/CREDIT pairs.
- **Input Validation**: Jakarta Validation on all request DTOs.
- **Custom Exceptions**: Proper HTTP status codes (404, 400, 409).
- **Structured Logging**: Separated log files (app.log, security.log, audit.log) with trace IDs.

---

## CI/CD Integration (GitLab Pipeline)

1. **Build**: Restores cache, runs Maven/NPM distributions.
2. **Test**: Executes JUnit (backend) and Vitest (frontend) tests.
3. **Docker-Build**: Packages images into GitLab Container registry.
4. **Deploy**: Configurable SSH deployment to production server.

---

## Future Improvements

- Add **Kibana / ELK Stack** for centralized log aggregation.
- Implement **RabbitMQ / Kafka** for async AuditLog processing.
- Add **2FA / TOTP** for enhanced authentication security.
- Introduce **Chart.js** for transaction history visualization on the dashboard.
- Add **Rate Limiting** to protect API endpoints from abuse.
- Implement **Account Statements** (PDF export).
