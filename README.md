# 🏦 Mini Core Banking System

A scalable, production-ready Fullstack banking application designed to handle high-concurrency environments securely.

Built with **Java Spring Boot 3** and **Vue.js 3**, it utilizes modern transaction safeguards (Optimistic Locking) and relies on robust data infrastructure (**PostgreSQL** and **Redis**) to prevent data races, ensuring absolute financial consistency.

---

## 🚀 Architecture & Tech Stack

### 🖥️ Frontend (Vue.js 3)
- **Framework**: Context API / Options API -> Vue 3 (Composition API / `<script setup>`)
- **State Management**: Pinia
- **Routing**: Vue Router
- **Web Client**: Axios with JWT Interceptors
- **Build Tool**: Vite
- **Deployment**: Nginx Alpine Docker container

### ⚙️ Backend (Java Spring Boot 3)
- **Framework**: Spring Boot 3.2.4 (Java 17)
- **Security**: Spring Security + JWT
- **Persistence Layer**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL 16 (Source of Truth, ACID compliant)
- **Cache**: Redis 7 (Speed optimizations, Token Blacklist, Sessions)
- **API Documentation**: OpenAPI / Swagger UI
- **Design Pattern**: Layered REST API (Controller -> Service -> Repository)

---

## ✨ Features

### 🔐 Authentication & Authorization
- Robust **JWT-based Authentication** with proper filtering.
- **Role-based Access Control** (`CUSTOMER` vs `ADMIN`).
- Token invalidation preparation with Redis blacklisting capabilities.

### 💰 Account Management
- **Customer Dashboard**: Overview of user accounts and balances.
- Creation of financial entities with strict database constraints.

### 💸 Transaction System (Concurrent Safety Layer)
- Supports: **Deposits**, **Withdrawals**, and internal **Transfers**.
- Protected with **`@Transactional`** boundaries.
- **Optimistic Locking (`@Version`)** implemented against race-conditions to prevent Double-Spending limits in high concurrent environments.

### 👑 Admin & Auditing Portal
- Advanced admin view for user and account monitoring.
- **Audit Logging System**: Asynchronous or separated transaction logging of all critical actions (Transfers, System Overrides).

---

## 🎨 System Design Explanation

*(See the `system-design.drawio.xml` file in the root. Import into [diagrams.net (Draw.io)](https://app.diagrams.net) to view visually.)*

1. **User Browser** connects over HTTPS and authenticates to obtain a JSON Web Token (JWT).
2. The **Vue.js Frontend** operates as a single-page app (SPA), hosted by Nginx. It attaches the `Authorization: Bearer <token>` onto subsequent Axios API calls.
3. The **Spring Boot API** filters requests. `JwtAuthenticationFilter` validates tokens (optionally cross-referencing a Redis token blacklist to force-logout compromised accounts). 
4. The service layer resolves database actions using **PostgreSQL**, relying on ACID properties to fulfill the core banking mechanics.
5. The **Audit Log System** writes immutable entries for important transactional activity (requires `REQUIRES_NEW` transaction propagations).

---

## 🗄️ Database Schema Overview

The core domains mapped within the database are tightly structured:
- **`users`**: Manages credentials and roles.
- **`accounts`**: Maintains exact `BigDecimal balance` values and tracks mutation variations via the `version` column (Optimistic Lock).
- **`transactions`**: Immutable ledger records detailing amounts transferred between `source_account_id` and `target_account_id`.
- **`audit_logs`**: System activity tracker pointing to affected users/actions.

---

## 🔌 API Documentation Section (Sample Endpoints)

If the server is running, you can access the OpenAPI (Swagger) UI at: 
`http://localhost:8080/swagger-ui/index.html`

### Authentication
- `POST /api/v1/auth/login` - Authenticate and get JWT.
- `POST /api/v1/auth/register` - Create customer account.

### Transactions
- `POST /api/v1/transactions/deposit` - Add to balance. Needs `{ "sourceAccountNumber": "...", "amount": 100 }`
- `POST /api/v1/transactions/transfer` - Send balance securely. Needs `{ "sourceAccountNumber": "...", "targetAccountNumber": "...", "amount": 50 }`
- `GET /api/v1/transactions/history/{accountNumber}` - Paginated ledger metrics logs.

### Administration
- `GET /api/v1/admin/users` - Fetches all users (Requires `ROLE_ADMIN`).
- `GET /api/v1/admin/audit-logs` - Inspects internal audit activities.

---

## 🛠️ Setup Instructions (Local Development)

### Prerequisites
- Docker & Docker Compose
- *Optionally for native runs:* JDK 17, Node 18, Postgres 16 instance.

### Option 1: Effortless Docker Deployment
Run everything (Frontend, Backend, DB, Redis) out-of-the-box using the provided multi-container orchestration.

```bash
docker-compose up --build -d
```
The Frontend will be available at `http://localhost:80`
The Backend API will be available at `http://localhost:8080`

### Option 2: Run Natively (Development)
You can mock the database via docker for externalized dev services:
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

## 🔑 Environment Variables Example (.env)

These are usually injected by Docker/GitLab secrets, but a reference `.env` might look like:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=banking_db
DB_USER=postgres
DB_PASS=Sup3rSecur3P@ssw0rd!

REDIS_HOST=localhost
REDIS_PORT=6379

JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_EXPIRATION_MS=86400000

APP_SECURITY_AUTH_WHITELIST="/api/v1/auth/**,/public/**"
```

---

## 🛡️ Security Considerations
- **JWT Authorization**: Enforces stateless and horizontally-scalable security protocols.
- **Hashing/Salting**: Uses Spring Security's `BCryptPasswordEncoder` to securely hash all passwords.
- **Race Condition Prevention**: Employs `@Version` and Strict database Transactioning methodologies, resolving critical fintech hazards like double spending.
- **Input Validation**: Backend guards against insufficient balance routing and empty parameter passing mechanisms.
- **Rate Limiting (Recommended)**: Further Nginx or Spring Cloud Gateway configurations should be explored in Production to handle DDoS attempts.

---

## 🚢 CI/CD Integration (GitLab Pipeline)

This project contains a fully operational `.gitlab-ci.yml` designed for robust release flows.
1. **Build**: Restores cache, runs Maven/NPM distributions.
2. **Test**: Spins native runners executing JUnit frameworks. 
3. **Docker-Build**: Packages lightweight Alpine images into the GitLab Container registry (`docker:dind`).
4. **Deploy**: Configurable step for pushing images directly into private staging/production SSH server boundaries.

---

## 🔮 Future Improvements

- Add a **Kibana / ELK Stack** for scalable microservice request tracing.
- Implement specialized event-queuing mechanisms (**RabbitMQ / Kafka**) for `AuditLog` decoupling.
- Extend Two-Factor Authentication (**2FA / TOTP**) protocols for external clients.
- Introduce advanced frontend Chart.js visualization logic for Account transaction histories.
