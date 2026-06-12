<div align="center">

<h1>💳 Fintech Wallet System</h1>

<p>
  <strong>A production-grade digital wallet backend built with Spring Boot 3.x</strong><br/>
  Modeled after real-world payment systems like GPay & PhonePe — not a tutorial CRUD app.
</p>

<p>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.5.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL"/>
  <img src="https://img.shields.io/badge/Redis-7.0-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis"/>
  <img src="https://img.shields.io/badge/Apache_Kafka-7.4.0-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka"/>
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
  <img src="https://img.shields.io/badge/Tests-166_passing-28a745?style=for-the-badge&logo=checkmarx&logoColor=white" alt="Tests"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="MIT License"/>
</p>

<p>
  <a href="#-features">Features</a> •
  <a href="#-architecture">Architecture</a> •
  <a href="#-api-reference">API</a> •
  <a href="#-engineering-deep-dive">Deep Dive</a> •
  <a href="#-getting-started">Getting Started</a> •
  <a href="#-testing">Testing</a>
</p>

</div>

---

## 🌟 What Makes This Project Different

This is not a "register/login/CRUD" portfolio project. Every design decision mirrors the patterns used by real fintech companies. Before a single line of code was written, the full system was designed — ERD, data flows, failure modes, and distributed patterns.

**What you'll find inside:**
- A **Lua-based atomic sliding-window rate limiter** running on Redis — the same approach used in production payment APIs
- A **double-entry bookkeeping ledger** so every rupee in the system is always accounted for
- **Kafka DLQ retry pipelines** so no failed notification is silently lost
- **Deadlock-safe pessimistic locking** on concurrent transfers using the ordered-lock technique
- **Idempotency key enforcement** so duplicate network requests never charge a user twice
- **JWT blacklisting on logout** — token revocation without sessions
- **166 tests** (84 unit + 82 integration) across real Testcontainers (MySQL + Redis + Kafka)

---

## 📋 Table of Contents

- [Features](#-features)
- [System Architecture](#-system-architecture)
- [Tech Stack](#-tech-stack)
- [Module Structure](#-module-structure)
- [API Reference](#-api-reference)
- [Data Model](#-data-model)
- [Engineering Deep Dive](#-engineering-deep-dive)
  - [Double-Entry Bookkeeping Ledger](#1-double-entry-bookkeeping-ledger)
  - [Sliding-Window Rate Limiter](#2-sliding-window-rate-limiter-atomic-lua-script)
  - [Idempotency Key Enforcement](#3-idempotency-key-enforcement)
  - [Deadlock-Safe Pessimistic Locking](#4-deadlock-safe-pessimistic-locking)
  - [Optimistic Locking via @Version](#5-optimistic-locking-via-version)
  - [Kafka DLQ & Failure Recovery](#6-kafka-dlq--persistent-failure-recovery)
  - [JWT Blacklisting on Logout](#7-jwt-token-blacklisting-on-logout)
  - [Auto Wallet on Registration](#8-automatic-wallet-creation-on-registration)
  - [System Ledger Accounts](#9-system-ledger-accounts-on-boot)
  - [Concurrency & Safety Guarantee Table](#concurrency--safety-guarantee-table)
- [Kafka Event Flow](#-kafka-event-flow)
- [Testing Strategy](#-testing-strategy)
- [Getting Started](#-getting-started)
- [Configuration Reference](#-configuration-reference)
- [Why Modular Monolith?](#-why-modular-monolith)

---

## ✨ Features

### 💰 Core Financial Operations
| Feature | Description |
|---|---|
| **Wallet Management** | One wallet per user, auto-created at registration. Active / Suspended / Closed lifecycle. |
| **Deposit** | Funds from the system liability account to user wallet. Rate-limited, ledger-recorded. |
| **Withdrawal** | Funds from user wallet back to system liability. Rate-limited, ledger-recorded. |
| **Peer-to-Peer Transfer** | Between any two wallets. Idempotent, deadlock-safe, fully ledgered. |
| **Transaction History** | Paginated history with filtering. Reference-ID lookup. |
| **Double-Entry Ledger** | Every movement creates a debit and credit pair. Permanent audit trail. |

### 🔒 Security & Authentication
| Feature | Description |
|---|---|
| **JWT Authentication** | Stateless — no sessions. Signed with HMAC-SHA256 (jjwt 0.12.3). |
| **Token Blacklisting** | Logout invalidates the token server-side via Redis with remaining-TTL expiry. |
| **BCrypt Passwords** | Password hashing at Spring Security default strength 10. |
| **Role-Based Access** | USER and ADMIN roles, extensible. |
| **Stateless Security Chain** | Spring Security configured fully stateless — `SessionCreationPolicy.STATELESS`. |

### ⚡ Reliability & Distributed Patterns
| Feature | Description |
|---|---|
| **Sliding-Window Rate Limiting** | Atomic Lua script on Redis. Per-user, per-operation (deposit/withdraw/transfer are tracked independently). |
| **Idempotency Keys** | Clients pass `idempotencyKey` on transfers — duplicate requests return the original result without re-executing. |
| **Optimistic Locking** | `@Version` on `Wallet` entity — concurrent updates produce HTTP 409 Conflict instead of silent data corruption. |
| **Pessimistic Locking** | `SELECT ... FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)` on deposit/withdraw; ordered lock acquisition on transfers eliminates deadlocks. |
| **Kafka Async Notifications** | Transaction events published to `transaction.created` (3 partitions). |
| **DLQ + Retry** | 3 retries with 1s backoff → `DeadLetterPublishingRecoverer` → `transaction.created.DLQ` → `FailedMessage` row persisted to DB. |
| **Duplicate DLQ Guard** | `DLQConsumer` checks `topic+offset` uniqueness before persisting — idempotent DLQ processing. |

### 📬 Notifications
| Feature | Description |
|---|---|
| **Async Notifications** | Kafka-driven. No in-request notification work. |
| **Per-Event Notifications** | TRANSFER creates two notifications (sender + receiver). DEPOSIT and WITHDRAWAL create one each. |
| **Read/Unread Status** | Mark individual or all notifications as read. |
| **Paginated Retrieval** | Both paginated and unpaginated notification endpoints. |

### 🛠️ Developer Experience
| Feature | Description |
|---|---|
| **Swagger / OpenAPI** | Full interactive docs at `/swagger-ui.html`. |
| **Docker Compose** | One-command startup: MySQL + Redis + Zookeeper + Kafka + App, all with healthchecks and `depends_on`. |
| **Multi-Stage Dockerfile** | Build stage (eclipse-temurin:21-jdk-alpine) → Runtime stage (eclipse-temurin:21-jre-alpine). Lean final image. |
| **Environment Variables** | All secrets injected at runtime via `.env`. A `.env.example` is included. |
| **JPA Auditing** | `BaseEntity` with `@CreatedDate` / `@LastModifiedDate` auto-populated on all entities. |
| **Unified API Response** | All endpoints return `ApiResponse<T>` with consistent `success`, `message`, `data` fields. |
| **Global Exception Handler** | Covers `MethodArgumentNotValidException`, `ConstraintViolationException`, `HandlerMethodValidationException`, `ObjectOptimisticLockingFailureException`, `BadCredentialsException`, and generic fallback. |

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CLIENT (HTTP / Swagger UI)                          │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ JWT Bearer Token
┌──────────────────────────────────▼──────────────────────────────────────────┐
│               Spring Boot 3.x Application  (Port 8080)                      │
│                                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────┐  ┌─────────────────┐  │
│  │    Auth     │  │   Wallet    │  │  Transaction  │  │     Ledger      │  │
│  │  Module     │  │   Module   │  │    Module     │  │     Module      │  │
│  │             │  │             │  │               │  │                 │  │
│  │ • Register  │  │ • Create   │  │ • Deposit     │  │ • Double-Entry  │  │
│  │ • Login     │  │ • Get by   │  │ • Withdraw    │  │   Record        │  │
│  │ • Logout    │  │   userId   │  │ • Transfer    │  │ • Account Mgmt  │  │
│  │ • JWT issue │  │ • Get by   │  │ • History     │  │ • Audit Trail   │  │
│  │ • Blacklist │  │   walletId │  │ • Idempotency │  │                 │  │
│  └─────────────┘  └─────────────┘  └───────────────┘  └─────────────────┘  │
│                                                                             │
│  ┌─────────────────────┐  ┌──────────────────────────────────────────────┐  │
│  │  Notification       │  │            User Module                       │  │
│  │  Module             │  │  • Profile fetch   • Profile update          │  │
│  │                     │  └──────────────────────────────────────────────┘  │
│  │ • Kafka Consumer    │                                                   │
│  │ • DLQ Consumer      │  ┌──────────────────────────────────────────────┐  │
│  │ • Mark read/unread  │  │         Common / Infrastructure Layer        │  │
│  └─────────────────────┘  │  Redis Service · JWT Filter · Global Handler │  │
│                            │  API Response Wrapper · JPA Auditing        │  │
│                            └──────────────────────────────────────────────┘  │
└──────┬────────────────────────────────────────────┬───────────────────┬──────┘
       │                                            │                   │
┌──────▼──────────────────┐         ┌───────────────▼─────┐  ┌─────────▼─────┐
│   MySQL 8.0             │         │   Redis 7.0          │  │  Apache Kafka │
│                         │         │                      │  │  (Confluent   │
│  • users                │         │  • jwt:blacklist:*   │  │   7.4.0)      │
│  • wallets              │         │  • idempotency:*     │  │               │
│  • transactions         │         │  • rate:limit:*      │  │  Topics:      │
│  • ledger_entries       │         │    transfer/deposit/ │  │  • txn.created│
│  • accounts             │         │    withdraw          │  │  • txn.DLQ    │
│  • notifications        │         └──────────────────────┘  │  • notif.send │
│  • failed_messages      │                                    └───────────────┘
└─────────────────────────┘
```

**Architectural choice — Modular Monolith over Microservices:** Each module (auth, wallet, transaction, ledger, notification) is a distinct bounded context with clean package separation, but shares one JVM and database. This provides transactional integrity across modules — a non-negotiable requirement for a financial system — without the distributed transaction complexity that would come from microservices. [Read the full reasoning →](#-why-modular-monolith)

---

## 🧰 Tech Stack

<table>
<tr>
<th>Layer</th>
<th>Technology</th>
<th>Purpose</th>
</tr>
<tr><td>Language / Runtime</td><td>Java 21, Spring Boot 3.5.x</td><td>Core application framework</td></tr>
<tr><td>Web</td><td>Spring MVC, Spring Security</td><td>REST API + stateless JWT security filter chain</td></tr>
<tr><td>Persistence</td><td>Spring Data JPA, Hibernate, MySQL 8.0</td><td>ORM, schema management (DDL auto-update), auditing</td></tr>
<tr><td>Cache / Atomic Ops</td><td>Redis 7.0 (Spring Data Redis)</td><td>JWT blacklist, idempotency keys, rate-limit counters</td></tr>
<tr><td>Messaging</td><td>Apache Kafka (Confluent 7.4.0), Spring Kafka</td><td>Async notification pipeline, DLQ recovery</td></tr>
<tr><td>Auth</td><td>jjwt 0.12.3, BCrypt</td><td>JWT signing/validation, password hashing</td></tr>
<tr><td>API Docs</td><td>SpringDoc OpenAPI / Swagger UI 2.8.9</td><td>Interactive API documentation</td></tr>
<tr><td>Containerization</td><td>Docker, Docker Compose</td><td>Full infrastructure stack in one command</td></tr>
<tr><td>Testing</td><td>JUnit 5, Mockito, Testcontainers 1.19.8, Awaitility 4.2.1</td><td>Unit + integration test coverage</td></tr>
<tr><td>Build</td><td>Maven, Lombok</td><td>Build automation, boilerplate reduction</td></tr>
</table>

---

## 📁 Module Structure

```
src/main/java/com/paymentprocessing/wallet/
│
├── 🔐 auth/                               # Authentication & Authorization
│   ├── controller/AuthController.java      # POST /api/auth/register|login|logout
│   ├── dto/                               # LoginRequest, RegisterRequest, AuthResponse
│   ├── security/
│   │   ├── JwtAuthFilter.java             # Per-request JWT validation + blacklist check
│   │   ├── JwtService.java                # Token generation, validation, remaining TTL
│   │   └── SecurityConfig.java            # Stateless filter chain, BCrypt bean, auth provider
│   └── service/impl/
│       ├── AuthServiceImpl.java            # register → save user → create wallet → issue JWT
│       └── TokenBlacklistServiceImpl.java  # Redis-backed JWT revocation
│
├── 👛 wallet/                             # Wallet Lifecycle
│   ├── controller/WalletController.java    # GET /api/wallets/me | /api/wallets/{id}
│   ├── entity/Wallet.java                 # @Version field for optimistic locking
│   ├── repository/WalletRepository.java   # findByUserIdForUpdate (PESSIMISTIC_WRITE)
│   └── service/impl/WalletServiceImpl.java
│
├── 💸 transaction/                        # Financial Operations (the core)
│   ├── controller/TransactionController.java
│   ├── dto/                               # DepositWithdrawRequest, TransferRequest, TransactionResponse
│   ├── entity/Transaction.java            # PENDING → SUCCESS / FAILED lifecycle + referenceId UNIQUE
│   └── service/impl/TransactionServiceImpl.java
│       # ↑ This is the most important class in the codebase.
│       # Contains: sliding-window rate limit (3 namespaces), idempotency
│       # check, ordered pessimistic lock acquisition, wallet balance update,
│       # ledger double-entry recording, Kafka event publish.
│
├── 📒 ledger/                             # Double-Entry Bookkeeping
│   ├── entity/
│   │   ├── Account.java                  # Code, name, type (ASSET/LIABILITY/REVENUE), balance
│   │   └── LedgerEntry.java              # DEBIT or CREDIT entry, linked to Account + referenceId
│   └── service/impl/LedgerServiceImpl.java  # recordDoubleEntry(), getOrCreateAccount()
│
├── 🔔 notification/                       # Async Event Processing
│   ├── kafka/
│   │   ├── KafkaTopicConfig.java          # 3-partition topics: transaction.created, .DLQ, notification.send
│   │   ├── NotificationProducer.java      # Publishes TransactionEvent to Kafka
│   │   ├── NotificationConsumer.java      # Consumes events — NO try/catch (lets retries fire)
│   │   ├── DLQConsumer.java              # Persists failed messages to DB (idempotent)
│   │   └── TransactionEvent.java          # Kafka event payload (referenceId, amount, type, status)
│   └── service/impl/NotificationServiceImpl.java
│       # processTransactionEvent() → creates 1 or 2 notifications per event type
│       # markAsRead() / markAllAsRead() / paginated retrieval
│
├── ⚙️ common/
│   ├── config/
│   │   ├── KafkaErrorConfig.java          # FixedBackOff(1s, 3 attempts) + DLQ routing
│   │   ├── KafkaProducerConfig.java
│   │   ├── RedisConfig.java               # StringRedisTemplate + GenericJackson2JsonRedisSerializer
│   │   ├── SwaggerConfig.java
│   │   └── DataInitializer.java           # Creates SYS-CASH, SYS-LIABILITY, SYS-REVENUE on startup
│   ├── entity/BaseEntity.java             # id (auto), @CreatedDate, @LastModifiedDate
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java    # Handles 8 exception types uniformly
│   │   ├── BadRequestException.java       # → HTTP 400
│   │   └── ResourceNotFoundException.java # → HTTP 404
│   ├── response/ApiResponse.java          # Unified {success, message, data} wrapper
│   └── service/RedisService.java          # get / set / exists / delete with TTL
│
└── 👤 user/                               # User Profile
    ├── controller/UserController.java
    ├── entity/User.java                   # email, fullName, role, BCrypt password
    └── service/impl/UserServiceImpl.java
```

---

## 📡 API Reference

Interactive docs: **`http://localhost:8080/swagger-ui.html`**

All authenticated endpoints require: `Authorization: Bearer <token>`

All responses follow the unified wrapper:
```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... }
}
```

### 🔐 Authentication

| Method | Endpoint | Auth | Description |
|---|---|:---:|---|
| `POST` | `/api/auth/register` | ❌ | Register a new user. Auto-creates wallet. Returns JWT. |
| `POST` | `/api/auth/login` | ❌ | Login with email + password. Returns JWT. |
| `POST` | `/api/auth/logout` | ✅ | Blacklists current JWT in Redis. Token unusable immediately. |

<details>
<summary><b>Register Request / Response Example</b></summary>

```json
// POST /api/auth/register
{
  "fullName": "Smooth Dev",
  "email": "smooth@example.com",
  "password": "securePassword123"
}

// Response 200
{
  "success": true,
  "data": {
    "userId": 1,
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "email": "smooth@example.com",
    "fullName": "Smooth Dev",
    "role": "USER"
  }
}
```
</details>

---

### 👛 Wallet

| Method | Endpoint | Auth | Description |
|---|---|:---:|---|
| `GET` | `/api/wallets/me` | ✅ | Get authenticated user's wallet (balance, status, ID) |
| `GET` | `/api/wallets/{walletId}` | ✅ | Get any wallet by ID |

---

### 💸 Transactions

| Method | Endpoint | Auth | Description |
|---|---|:---:|---|
| `POST` | `/api/transactions/deposit` | ✅ | Deposit funds. Rate-limited (5/min). Ledger-recorded. |
| `POST` | `/api/transactions/withdraw` | ✅ | Withdraw funds. Rate-limited (5/min). Ledger-recorded. |
| `POST` | `/api/transactions/transfer` | ✅ | Transfer to another wallet. Idempotent. Rate-limited (5/min). |
| `GET` | `/api/transactions/{referenceId}` | ✅ | Look up a specific transaction by reference ID |
| `GET` | `/api/transactions/history?page=0&size=10` | ✅ | Paginated transaction history for authenticated user |

<details>
<summary><b>Transfer Request Example (with idempotency)</b></summary>

```json
// POST /api/transactions/transfer
{
  "receiverWalletId": 2,
  "amount": 500.00,
  "description": "Split bill",
  "idempotencyKey": "txn-client-uuid-abc123"
}

// Response 200
{
  "success": true,
  "data": {
    "id": 42,
    "senderWalletId": 1,
    "receiverWalletId": 2,
    "amount": 500.0000,
    "type": "TRANSFER",
    "status": "SUCCESS",
    "referenceId": "c1e2f3a4-...",
    "description": "Split bill",
    "createdAt": "2026-06-12T10:30:00"
  }
}
```

Resending the same `idempotencyKey` returns the **identical response** without re-executing the transfer.
</details>

---

### 📒 Ledger

| Method | Endpoint | Auth | Description |
|---|---|:---:|---|
| `GET` | `/api/ledger/{referenceId}` | ✅ | Get the DEBIT + CREDIT pair for a transaction |
| `GET` | `/api/ledger/account/{accountId}` | ✅ | Get all ledger entries for a given account |

<details>
<summary><b>Ledger Entry Example (Transfer of Rs. 500)</b></summary>

```json
// GET /api/ledger/c1e2f3a4-...
[
  {
    "accountCode": "USR-1",
    "type": "DEBIT",
    "amount": 500.0000,
    "referenceId": "c1e2f3a4-..."
  },
  {
    "accountCode": "USR-2",
    "type": "CREDIT",
    "amount": 500.0000,
    "referenceId": "c1e2f3a4-..."
  }
]
```
</details>

---

### 🔔 Notifications

| Method | Endpoint | Auth | Description |
|---|---|:---:|---|
| `GET` | `/api/notifications` | ✅ | All notifications for authenticated user |
| `GET` | `/api/notifications/unread` | ✅ | Unread notifications only |
| `PATCH` | `/api/notifications/{id}/read` | ✅ | Mark one notification as read |
| `PATCH` | `/api/notifications/read-all` | ✅ | Mark all notifications as read |

---

### 👤 User

| Method | Endpoint | Auth | Description |
|---|---|:---:|---|
| `GET` | `/api/users/me` | ✅ | Get authenticated user's profile |
| `PUT` | `/api/users/me` | ✅ | Update profile (fullName, etc.) |

---

## 🗄️ Data Model

```
┌─────────────────────────────────────────────────────────────────────┐
│  users                                                              │
│  id PK │ email UNIQUE │ full_name │ password (BCrypt) │ role        │
│  created_at │ updated_at (JPA auditing)                             │
└─────────────────────────┬───────────────────────────────────────────┘
                          │ 1:1
┌─────────────────────────▼───────────────────────────────────────────┐
│  wallets                                                            │
│  id PK │ user_id FK UNIQUE │ balance DECIMAL(19,4)                  │
│  status ENUM(ACTIVE,SUSPENDED,CLOSED) │ version (optimistic lock)   │
│  created_at │ updated_at                                            │
└──────────────┬────────────────────────────┬────────────────────────┘
               │ sender_wallet_id           │ receiver_wallet_id
┌──────────────▼────────────────────────────▼────────────────────────┐
│  transactions                                                       │
│  id PK │ sender_wallet_id FK nullable │ receiver_wallet_id FK null. │
│  amount DECIMAL(19,4) │ type ENUM(DEPOSIT,WITHDRAWAL,TRANSFER)      │
│  status ENUM(PENDING,SUCCESS,FAILED) │ reference_id UNIQUE          │
│  description │ failure_reason │ created_at │ updated_at             │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  accounts  (Ledger Accounts)                                        │
│  id PK │ code UNIQUE (USR-{walletId} | SYS-LIABILITY | SYS-CASH)   │
│  name │ type ENUM(ASSET,LIABILITY,REVENUE) │ balance DECIMAL(19,4)  │
│  active │ created_at │ updated_at                                   │
└─────────────────────────┬───────────────────────────────────────────┘
                          │ 1:N
┌─────────────────────────▼───────────────────────────────────────────┐
│  ledger_entries                                                     │
│  id PK │ account_id FK │ type ENUM(DEBIT,CREDIT)                    │
│  amount DECIMAL(19,4) │ reference_id (groups the debit+credit pair) │
│  description │ created_at │ updated_at                              │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  notifications                                                      │
│  id PK │ user_id │ title │ message │ type │ reference_id            │
│  status ENUM(PENDING,READ) │ created_at │ updated_at                │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  failed_messages  (DLQ persistence)                                 │
│  id PK │ topic │ offset │ payload TEXT │ error_reason               │
│  status ENUM(PENDING,RESOLVED) │ created_at │ updated_at            │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🔬 Engineering Deep Dive

### 1. Double-Entry Bookkeeping Ledger

Every financial movement — deposit, withdrawal, transfer — produces **exactly two `LedgerEntry` rows** sharing the same `referenceId`. This is the same principle that banks use. Every rupee in the system can be traced, and the books always balance.

```
Transfer of Rs. 500 from Wallet-1 → Wallet-2:
  DEBIT   USR-1    Rs. 500.00   (ref: c1e2f3a4)   ← money leaves
  CREDIT  USR-2    Rs. 500.00   (ref: c1e2f3a4)   ← money arrives

Deposit of Rs. 1000 into Wallet-1:
  DEBIT   SYS-LIABILITY   Rs. 1000.00  (ref: d4e5f6a7)  ← system owes less
  CREDIT  USR-1           Rs. 1000.00  (ref: d4e5f6a7)  ← user gains

Withdrawal of Rs. 200 from Wallet-1:
  DEBIT   USR-1         Rs. 200.00   (ref: e7f8a9b0)   ← user loses
  CREDIT  SYS-LIABILITY Rs. 200.00   (ref: e7f8a9b0)   ← system owes more
```

`SYS-LIABILITY` represents the system's aggregate obligation to all users. On startup, `DataInitializer` creates `SYS-CASH`, `SYS-LIABILITY`, and `SYS-REVENUE` system accounts so they are always available for the first transaction.

---

### 2. Sliding-Window Rate Limiter (Atomic Lua Script)

The rate limiter uses a **sliding-window counter** that eliminates the burst-at-boundary problem of fixed-window counters. A user who sends 5 requests at 11:59:55 cannot immediately send 5 more at 12:00:01 — the previous window's count bleeds into the current window proportionally.

**The formula:**
```
effective_count = (current_window_count + 1) + previous_window_count × (1 - elapsed_fraction)
```

**Why a Lua script?** The entire check-and-increment must be atomic. If you use a separate `GET` then `INCR`, two concurrent requests can both pass the `GET` check before either runs `INCR`, allowing a burst through. The Lua script runs atomically on Redis — no race condition is possible.

**Why `StringRedisTemplate`?** The default `RedisTemplate` serializes values as JSON (storing `5` as `"5"` with quotes). Redis `INCR` requires a raw integer string. Using `StringRedisTemplate` stores values as plain integers, making `INCR` work correctly. This is documented explicitly in the source code.

```java
// Three independent rate limit namespaces — limits tracked separately
private static final String RATE_LIMIT_KEY_TRANSFER  = "rate:limit:transfer:";
private static final String RATE_LIMIT_KEY_DEPOSIT   = "rate:limit:deposit:";
private static final String RATE_LIMIT_KEY_WITHDRAW  = "rate:limit:withdraw:";
```

A user can deposit 5 times AND transfer 5 times in the same minute — operations don't share a limit.

---

### 3. Idempotency Key Enforcement

Clients that experience a network timeout don't know if their request succeeded. Retrying naively could double-charge the user. The idempotency key solves this:

```
First request  (key: "client-uuid-abc123") → executes transfer → stores (key → referenceId) in Redis, TTL 24h → returns result
Retry request  (key: "client-uuid-abc123") → key found in Redis → returns original result without re-executing
```

The reference ID stored in Redis maps directly to the original `Transaction` row, so the retry response is byte-for-byte identical to the original — including all amounts, timestamps, and status.

---

### 4. Deadlock-Safe Pessimistic Locking

Concurrent transfers between wallets A→B and B→A would deadlock if both acquired locks in the order they happen to reach the code. The classic solution: **always acquire locks in ascending ID order**.

```java
// Step 1: resolve IDs (no locks yet)
Long senderWalletId   = senderWalletRef.getId();
Long receiverWalletId = request.getReceiverWalletId();

// Step 2: lock in ascending order — ALWAYS
Long firstId  = Math.min(senderWalletId, receiverWalletId);
Long secondId = Math.max(senderWalletId, receiverWalletId);

Wallet firstWallet  = walletRepository.findByIdForUpdate(firstId);   // SELECT ... FOR UPDATE
Wallet secondWallet = walletRepository.findByIdForUpdate(secondId);  // SELECT ... FOR UPDATE

// Step 3: re-map to semantic roles
Wallet senderWallet   = firstWallet.getId().equals(senderWalletId) ? firstWallet  : secondWallet;
Wallet receiverWallet = firstWallet.getId().equals(senderWalletId) ? secondWallet : firstWallet;
```

For deposits and withdrawals (single-wallet operations), `findByUserIdForUpdate` acquires a pessimistic write lock that eliminates the time-of-check/time-of-use window between the balance check and the balance update.

---

### 5. Optimistic Locking via `@Version`

The `Wallet` entity has a `@Version Long version` field. JPA automatically appends `WHERE version = ?` to every UPDATE. If two transactions both read version 5 and both try to commit, the second one sees `0 rows affected` and Spring throws `ObjectOptimisticLockingFailureException`. The `GlobalExceptionHandler` catches this and returns HTTP 409 Conflict.

```java
@Version
private Long version;   // in Wallet.java
```

```java
// In GlobalExceptionHandler.java
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(...) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("Transaction conflict detected. Please retry your request."));
}
```

This acts as a second safety net: pessimistic locking prevents the problem, optimistic locking detects any edge cases that slip through.

---

### 6. Kafka DLQ & Persistent Failure Recovery

The notification pipeline is architected so **no message failure is silently swallowed**:

```
TransactionServiceImpl
  └─→ NotificationProducer.sendTransactionEvent()
        └─→ Kafka: transaction.created (3 partitions)
              └─→ NotificationConsumer
                    ├── SUCCESS → NotificationService.processTransactionEvent() → DB row
                    └── EXCEPTION (propagates — no try/catch)
                          └─→ KafkaErrorConfig.DefaultErrorHandler
                                └── FixedBackOff(1000ms, 3 attempts)
                                      └── after 3 failures → DeadLetterPublishingRecoverer
                                            └─→ Kafka: transaction.created.DLQ
                                                  └─→ DLQConsumer
                                                        └── check topic+offset uniqueness
                                                              └── FailedMessage → DB (PENDING)
```

**Why no try/catch in `NotificationConsumer`?** If you catch the exception, Kafka acknowledges the offset and the message is permanently lost. Letting it propagate gives Spring Kafka's error handler control over retries and DLQ routing — which is the correct pattern.

---

### 7. JWT Token Blacklisting on Logout

Standard JWT is stateless — you can't "invalidate" a token that has already been issued. The solution: store the token in Redis with a TTL equal to its remaining validity.

```
On logout:
  1. Extract remaining TTL from JWT claims
  2. If TTL ≤ 0: token already expired, nothing to do
  3. Else: SET jwt:blacklist:{token} "blacklisted" EX {remaining_seconds}

On every authenticated request (JwtAuthFilter):
  1. Validate JWT signature and expiry
  2. Check EXISTS jwt:blacklist:{token}
  3. If exists: reject with 401
  4. Else: allow through
```

When the token's natural expiry arrives, Redis automatically evicts the key. No cleanup job needed.

---

### 8. Automatic Wallet Creation on Registration

A wallet is created atomically in the same database transaction as user registration:

```java
// AuthServiceImpl.register()
User savedUser = userRepository.save(user);
walletService.createWallet(savedUser.getId());  // same @Transactional scope
String token = jwtService.generateToken(savedUser.getEmail());
```

If wallet creation fails, the entire registration rolls back — users never exist without a wallet.

---

### 9. System Ledger Accounts on Boot

`DataInitializer` implements `ApplicationRunner` and creates the three system accounts on every startup if they don't already exist:

| Account Code | Type | Purpose |
|---|---|---|
| `SYS-CASH` | ASSET | System's cash holdings |
| `SYS-LIABILITY` | LIABILITY | System's obligation to all users |
| `SYS-REVENUE` | REVENUE | System revenue (fees, future use) |

This guarantees the accounts exist before the first deposit transaction runs, preventing a startup-race condition.

---

### Concurrency & Safety Guarantee Table

| Scenario | What Could Go Wrong | Mechanism | HTTP Response on Violation |
|---|---|---|---|
| Two concurrent transfers A→B and B→A | Deadlock | Pessimistic lock in ascending wallet-ID order | Never deadlocks |
| Concurrent deposits to same wallet | Lost update (balance wrong) | `SELECT ... FOR UPDATE` | Correct — second blocks until first commits |
| Two tabs submitting same form | Double charge | Idempotency key (Redis 24h TTL) | Second returns original result |
| Concurrent balance read+update | TOCTOU race | Pessimistic write lock | Correct |
| Two services update same wallet row | Stale read overwrite | Optimistic `@Version` lock | 409 Conflict |
| Too many requests per minute | Resource exhaustion | Sliding-window Lua rate limiter | 400 Bad Request |
| Kafka message processing failure | Silent data loss | 3 retries + DLQ + DB persist | No data lost |
| User reuses token after logout | Unauthorized access | JWT blacklist in Redis | 401 Unauthorized |
| Duplicate DLQ delivery | Duplicate failed_message row | topic+offset uniqueness check | Skipped idempotently |

---

## 📨 Kafka Event Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│  TransactionServiceImpl                                               │
│  (after successful deposit / withdraw / transfer)                    │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ TransactionEvent {referenceId, amount,
                               │   type, senderUserId, receiverUserId}
┌──────────────────────────────▼───────────────────────────────────────┐
│  NotificationProducer                                                 │
│  topic: transaction.created (3 partitions, replication=1)            │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
         ┌─────────────────────▼──────────────────────┐
         │  NotificationConsumer (group: wallet-group) │
         │  @KafkaListener — no try/catch              │
         └──────────────────────┬─────────────────────┘
                                │
            ┌───────────────────┴───────────────────┐
            │ SUCCESS                               │ EXCEPTION
            ▼                                       ▼
  NotificationService              DefaultErrorHandler
  .processTransactionEvent()       FixedBackOff(1s × 3)
            │                               │
  Creates 1-2 Notification rows            │ after 3 failures
  in MySQL (PENDING status)                ▼
                              DeadLetterPublishingRecoverer
                              topic: transaction.created.DLQ
                                           │
                              ┌────────────▼──────────────┐
                              │  DLQConsumer               │
                              │  (group: wallet-dlq-group) │
                              │  idempotent: skips if      │
                              │  topic+offset seen before  │
                              └────────────┬──────────────┘
                                           │
                              FailedMessage saved to MySQL
                              status: PENDING (awaiting manual replay)
```

**Kafka Topics:**

| Topic | Partitions | Consumer Group | Purpose |
|---|:---:|---|---|
| `transaction.created` | 3 | `wallet-group` | Primary notification pipeline |
| `transaction.created.DLQ` | 3 | `wallet-dlq-group` | Dead letters after 3 failed retries |
| `notification.send` | 3 | — | Reserved for future push/email delivery |

---

## 🧪 Testing Strategy

**166 tests total — 84 unit + 82 integration**

Every unit test uses Mockito to isolate the class under test. Every integration test spins up real MySQL, Redis, and Kafka containers via Testcontainers and exercises the full HTTP stack through `MockMvc`.

### Test Infrastructure

```java
// IntegrationTestBase.java — all integration tests extend this
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTestBase {
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.0").withReuse(true);
    static final KafkaContainer kafka = new KafkaContainer("confluentinc/cp-kafka:7.4.0").withReuse(true);

    static { mysql.start(); redis.start(); kafka.start(); }  // started once, reused across all classes
}
```

`withReuse(true)` means Docker containers are created once per test suite run — dramatically cutting CI time.

### Unit Tests (84 tests)

| Test Class | Tests | What's Covered |
|---|:---:|---|
| `TransactionServiceImplTest` | 10 | Rate limit enforcement, idempotency, insufficient balance, transfer validations |
| `LedgerServiceImplTest` | 11 | Double-entry creation, balance updates, account-not-found errors |
| `UserServiceImplTest` | 10 | Profile fetch, update, not-found cases |
| `NotificationServiceImplTest` | 9 | Event → notification mapping for TRANSFER/DEPOSIT/WITHDRAWAL |
| `WalletServiceImplTest` | 6 | Create wallet, duplicate prevention, not-found |
| `AuthServiceImplTest` | 6 | Register/login validation, duplicate email, token generation |
| `AuthControllerTest` | 10 | HTTP request/response contract, validation errors |
| `WalletControllerTest` | 9 | Controller layer with `@WebMvcTest` |
| `TransactionControllerTest` | 12 | Request validation, auth context, pagination |

### Integration Tests (82 tests)

| Test Class | Tests | What's Proved End-to-End |
|---|:---:|---|
| `LedgerIntegrationTest` | 16 | DEBIT+CREDIT rows created for all 3 transaction types; balances match |
| `NotificationIntegrationTest` | 14 | Full async pipeline with Awaitility; 2 notifications per transfer |
| `TransactionIntegrationTest` | 13 | Deposit/withdraw flow, balance consistency, error cases |
| `AuthIntegrationTest` | 10 | Register→login→logout→rejected reuse of blacklisted token |
| `UserIntegrationTest` | 10 | Profile read/update with real DB |
| `KafkaNotificationIntegrationTest` | 3 | Message published, consumed, notification row in DB |
| `RateLimiterIntegrationTest` | 5 | 6th request in same minute rejected with 400 |
| `TransferIntegrationTest` | 4 | Cross-wallet transfer; both balances correct after transfer |
| `WalletIntegrationTest` | 4 | Wallet auto-created on register; balance queries |
| `IdempotencyTest` | 2 | Duplicate transfer key → identical response, one DB row |

### Running Tests

```bash
# Unit tests only (no Docker required)
./mvnw test -Dtest="*ServiceImplTest,*ControllerTest"

# All tests including integration (Docker required)
./mvnw verify

# Specific integration test
./mvnw test -Dtest="TransferIntegrationTest"
```

---

## 🚀 Getting Started

### Prerequisites

- **Docker** and **Docker Compose** (for the full stack)
- **Java 21** (only needed for local development without Docker)

### Running with Docker Compose (Recommended)

**1. Clone the repository**
```bash
git clone https://github.com/your-username/fintech-wallet-system.git
cd fintech-wallet-system
```

**2. Set up environment variables**
```bash
cp .env.example .env
```

Edit `.env` with your values:
```env
# Database
DB_ROOT_PASSWORD=your_secure_root_password
DB_NAME=wallet_system
DB_USERNAME=wallet_user
DB_PASSWORD=your_db_password

# JWT — generate with: openssl rand -hex 32
JWT_SECRET=replace_with_64_char_hex_string_from_openssl
JWT_EXPIRATION=86400000
```

**3. Start the full stack**
```bash
docker compose up --build
```

Docker Compose starts services in order with healthcheck dependencies:
```
Zookeeper → Kafka → MySQL → Redis → App
```

The app will not start until all four infrastructure services pass their healthchecks.

**4. Access the API**

| URL | Description |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive API docs |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON spec |
| `http://localhost:8080/api/auth/register` | First endpoint to hit |

MySQL is exposed on port `3307` (not 3306) to avoid conflicts with a local MySQL install.

---

### Running Locally (App only, infrastructure in Docker)

```bash
# Start only the infrastructure services
docker compose up mysql redis zookeeper kafka

# Run the Spring Boot app with required env vars
export APPLICATION_JWT_SECRET=your_64_char_secret_here
./mvnw spring-boot:run
```

---

### Quick API Walkthrough

```bash
# 1. Register a user (wallet is auto-created)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Alice","email":"alice@test.com","password":"pass123"}'

# 2. Login to get a JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"pass123"}' | jq -r '.data.token')

# 3. Check your wallet
curl http://localhost:8080/api/wallets/me \
  -H "Authorization: Bearer $TOKEN"

# 4. Deposit funds
curl -X POST "http://localhost:8080/api/transactions/deposit?amount=1000" \
  -H "Authorization: Bearer $TOKEN"

# 5. Transfer to another wallet (idempotent)
curl -X POST http://localhost:8080/api/transactions/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"receiverWalletId":2,"amount":250,"idempotencyKey":"my-unique-key-001"}'

# 6. View ledger entries for the transfer
curl http://localhost:8080/api/ledger/{referenceId} \
  -H "Authorization: Bearer $TOKEN"
```

---

## ⚙️ Configuration Reference

All configuration can be overridden via environment variables.

| Property | Env Variable | Default | Notes |
|---|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/wallet_system` | Auto-creates DB if absent |
| `spring.datasource.username` | `DB_USERNAME` | `root` | |
| `spring.datasource.password` | `DB_PASSWORD` | _(empty)_ | |
| `spring.data.redis.host` | `SPRING_DATA_REDIS_HOST` | `localhost` | |
| `spring.data.redis.port` | `SPRING_DATA_REDIS_PORT` | `6379` | |
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker list |
| `application.jwt.secret` | `APPLICATION_JWT_SECRET` | _(required)_ | Min 32 bytes; generate with `openssl rand -hex 32` |
| `application.jwt.expiration` | `APPLICATION_JWT_EXPIRATION` | `86400000` | Token TTL in ms (default: 24 hours) |
| `app.rate-limit.max-per-minute` | — | `5` | Max ops/min per user per operation type |

---

## 🤔 Why Modular Monolith?

This is a deliberate architectural decision, not a limitation.

**The problem with microservices for a financial system:**
A transfer involves multiple domain operations — wallet balance updates, transaction record creation, ledger entries, and Kafka event publishing — that must all succeed or all fail together. In a microservices architecture, these become distributed transactions requiring saga patterns or two-phase commit, adding significant complexity and failure modes.

**What the modular monolith gives us:**
- All of the above operations execute in a single `@Transactional` boundary — true ACID guarantees
- Service-to-service calls are in-process (zero network latency, no availability concerns)
- Bounded context separation is enforced by package structure, not network topology
- Kafka handles the one place where eventual consistency is genuinely appropriate: notification delivery

**What still scales well:**
- Redis-based rate limiting, idempotency, and JWT blacklisting work identically in a distributed deployment
- Kafka topics use 3 partitions — ready for horizontal consumer scaling
- The Dockerfile produces a lean JRE image that can be deployed to any container orchestrator

**When microservices would make sense:**
When individual modules (e.g., ledger, notifications) need independent deployment cycles, separate scaling policies, or different technology stacks. The current architecture is designed to make that extraction straightforward — bounded contexts are already clean.

---

## 📐 Design Principles Applied

| Principle | Where Applied |
|---|---|
| **Design-first** | ERD, data flows, and concurrency patterns were defined before any code was written |
| **`BigDecimal` for money** | All financial amounts use `DECIMAL(19,4)` in MySQL and `BigDecimal` in Java — no floating-point errors |
| **`@Transactional(readOnly = true)` on service classes** | Promotes read replicas, reduces unnecessary write locks. Write operations override per-method. |
| **Explicit `@Lock` annotations** | Locking intent is visible at the repository interface level — no hidden behaviour |
| **Structured error responses** | All errors return `ApiResponse<Void>` with a human-readable message — never raw exceptions |
| **Environment-only secrets** | No credentials in source code. `.env.example` documents every required variable. |
| **Separation of concerns** | Controllers own HTTP concerns; Services own business logic; Repositories own persistence logic |

---

## 📄 License

This project is licensed under the MIT License.

---

<div align="center">

Built with care as a serious backend portfolio project.<br/>
If this helped you, please ⭐ star the repository.

[↑ Back to top](#-fintech-wallet-system)

</div>
