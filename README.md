# Money Transfer System

A microservice-based money-transfer platform built for high concurrency and correctness. A client
moves money from their account to another; the system screens it, moves it atomically, records it in
an immutable ledger, and keeps everything consistent under load.

Built with **Spring Boot 4.1 / Java 25**, **PostgreSQL**, **Redis**, and **Kafka (Redpanda)**.

---

## Architecture

```
                       ┌───────────────── API Gateway (nginx :8088) ─────────────────┐
   client ────────────►│         routing + rate limiting (single entry point)        │
                       └──────┬──────────────────────────┬─────────────────────────┬─┘
                              │                          │                         │
                     /api/transfers                  /api/clients             /api/ledger-accounts
                              │                          │                         │
                              ▼                          ▼                         ▼
                   ┌──────────────┐                ┌──────────────┐         ┌──────────────┐
                   │   Transfer   │                │   Account    │         │    Ledger    │
                   │  (:8080)     │                │   (:8082)    │         │   (:8083)    │
                   │ orchestrator │                │  balances    │         │ double-entry │
                   └──────┬───────┘                └──────┬───────┘         └──────▲───────┘
                          │  1. fraud check (sync,stub)   │                        │
                          │  2. apply money-move──────────►                        │ (Account moves money)
                          │     (sync HTTP, resilient)                             │
                          │  3. write outbox + publish ──► Kafka ──────────────────┘ (Ledger consumes)
                          ▼
                   transfer-db                     account-db + Redis                 ledger-db
```

**The flow of one transfer:**

1. Client calls `POST /api/transfers` (through the gateway → transfer service).
2. **Transfer service** persists the transfer as `PENDING`, runs a **fraud check** (synchronous), then
   calls the **account service** to move the money (synchronous, the critical path).
3. **Account service** atomically debits the source and credits the destination in one ACID
   transaction (with overdraft and currency checks), and returns success/decline.
4. Transfer service marks the transfer `COMPLETED` and writes an event to its **outbox** — in the same
   database transaction, so the event can never be lost.
5. An **outbox relay** publishes the event to **Kafka**. The **ledger service** consumes it and writes
   the immutable **double-entry** record. (Notifications would be a second consumer of the same event.)

Steps 1–3 are **synchronous** (the client waits). Steps 4–5 are **asynchronous / eventually
consistent** — the ledger trails the money-move but never loses an event.

---

## Services

### Transfer service (`transfer/`, port 8080)
The **saga orchestrator**. It owns the transfer's *state*, not the money.
- `POST /api/transfers` — create a transfer; `GET /api/transfers/{id}` — check status.
- Coordinates the saga: validate → dedupe → `PENDING` → fraud check → account money-move → finalize.
- **Idempotency:** a client-supplied `idempotencyKey` (unique in the DB) makes retries safe — the same
  key never moves money twice.
- **Resilience:** retry + circuit breaker (Resilience4j) on the synchronous fraud/account calls.
- **Transient vs terminal:** a downstream *outage* leaves the transfer `PENDING` (retryable, HTTP 503);
  a downstream *decision* (insufficient funds, fraud reject) is terminal (`FAILED` / `BLOCKED`).
- **Transactional outbox** + scheduled relay (publishes events to Kafka, `SELECT … FOR UPDATE SKIP LOCKED`).
- **Reconciler:** a background job that resumes or expires transfers stuck `PENDING` (e.g. after a crash).
- States: `PENDING`, `COMPLETED`, `FAILED`, `BLOCKED`, `EXPIRED`.

### Account service (`account/`, port 8082)
The **authoritative owner of balances** — the only thing allowed to change an account balance.
- `POST /internal/transfers` — atomically debit + credit (locks both rows in sorted order to avoid
  deadlocks; enforces overdraft, currency match, and account status). Idempotent on `transferId`.
- `GET /api/clients/{clientId}/accounts` — list a client's accounts, **cached in Redis**
  (evicted when a balance changes).
- Domain: a **client** owns many **accounts** (one balance per currency).

### Ledger service (`ledger/`, port 8083)
The **immutable double-entry record** — the system of record for audit and reconciliation.
- Consumes transfer events from Kafka and writes one balanced **journal entry** per transfer
  (a `DEBIT` and a `CREDIT` that sum to zero). Idempotent on `transferId`.
- `GET /api/ledger-accounts/{ref}/balance` — balance **derived** by summing postings
  (used to reconcile against the account service).
- `GET /api/ledger-accounts/{ref}/postings` — a statement of all entries for an account.

### API Gateway (`gateway/`, nginx, port 8088)
A single entry point that routes by path and applies basic per-client rate limiting.
Routes: `/api/transfers→8080`, `/api/clients→8082`, `/api/ledger-accounts→8083`.
(No authentication yet — left as a follow-up.)

---

## Background jobs (transfer service)

Two scheduled jobs run inside the transfer service to keep the system consistent with no one watching.

### Outbox relay
Every ~1s it reads `PENDING` outbox rows (`SELECT … FOR UPDATE SKIP LOCKED`), publishes them to Kafka,
and marks them `PUBLISHED`. A failed publish leaves the row `PENDING` to be retried next tick
(at-least-once delivery). `SKIP LOCKED` makes it safe to run multiple instances.

### Reconciler
A **safety net** for transfers stuck in `PENDING` — e.g. the service crashed mid-saga, or a downstream
was unavailable and the client never retried. Without it, such a transfer would sit unresolved forever
(the request thread that was handling it is gone). It guarantees **every transfer eventually reaches a
terminal state**, with zero client cooperation.

On each tick it:
1. **Claims** `PENDING` transfers that have been quiet for at least `min-age` — it locks them with
   `SKIP LOCKED` and bumps their timestamp, so it never races a request that's actively processing one,
   and concurrent reconcilers take disjoint work.
2. For each claimed transfer: if it's older than `max-age`, it **gives up and marks it `EXPIRED`**;
   otherwise it **resumes the saga** (re-runs fraud → account → finalize). Resuming is safe because
   every downstream call is idempotent on `transferId`.

Timing is configurable (defaults shown):

| Property | Default | Role |
|---|---|---|
| `reconcile.interval-ms` | 10s | how often the sweep runs (`fixedDelay`) |
| `reconcile.min-age-ms` | 30s | how long a transfer must be quiet before it's eligible (avoids racing in-progress requests; also throttles how often any one transfer is retried) |
| `reconcile.max-age-ms` | 1h | when to give up and expire |

Because the claim bumps the timestamp, a stuck transfer is retried roughly every `min-age` (~30s) until
it completes or ages out — not on every 10s tick.

---

## Key patterns

| Pattern | Where | Why |
|---|---|---|
| **Saga orchestration** | transfer | coordinate a multi-step flow across services without a distributed transaction |
| **Authoritative balance** | account | one owner of money; the ledger is a downstream record, not the source of truth |
| **Idempotency keys** | every hop | safe retries under at-least-once delivery; no double-spend |
| **Transactional outbox** | transfer | publish events to Kafka without losing them (no dual-write problem) |
| **Double-entry** | ledger | provable correctness — every movement balances; money is conserved |
| **Resilience4j** | transfer | retry + circuit breaker on synchronous calls |

---

## Infrastructure

Everything runs via `docker-compose.yml`:

| Component | Port | Used by |
|---|---|---|
| transfer-db (Postgres) | 5433 | transfer |
| account-db (Postgres) | 5434 | account |
| ledger-db (Postgres) | 5435 | ledger |
| account-cache (Redis) | 6379 | account (account-list cache) |
| kafka (Redpanda) | 9092 | transfer → ledger events (`ledger.postings`) |
| gateway (nginx) | 8088 | single entry point |

Each service uses **in-memory H2 by default** (so tests and quick runs need no infrastructure), and
switches to its **Postgres database + Flyway migrations** under the `postgres` profile.

---

## Running locally

```bash
# 1. start infrastructure
docker compose up -d

# 2. start the services (each in its own terminal), Postgres-backed
cd account  && SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
cd ledger   && SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
cd transfer && OUTBOX_PUBLISHER=kafka SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

Send a transfer (through the gateway):

```bash
curl -X POST http://localhost:8088/api/transfers \
  -H 'Content-Type: application/json' \
  -d '{
    "idempotencyKey": "tx-001",
    "fromAccountId": "<an account uuid>",
    "toAccountId":   "<another account uuid>",
    "amount": 50,
    "currency": "KGS"
  }'
```

> The account service is the source of truth for accounts. Seed clients/accounts into `account-db`
> (there's no account-creation endpoint yet), then use those UUIDs above.

### Tests

```bash
cd transfer && ./mvnw test     # 29 tests
cd account  && ./mvnw test     # 15 tests
cd ledger   && ./mvnw test     #  8 tests
```

Tests run on H2 and need no external services.

### Load test

A [k6](https://k6.io) harness lives in `load/` (`load.js` + seeded `accounts.json`). It drives the
transfer endpoint with unique idempotency keys across many accounts — the system sustains **1000+ TPS**
with zero errors and provably conserves money (all balances still sum to the seeded total).

---

## Tech stack

- **Java 25**, **Spring Boot 4.1** (Web, Data JPA, Validation, Kafka, Cache)
- **PostgreSQL 16** + **Flyway** (versioned schema, `validate` on)
- **Redis** (account-list cache), **Redpanda** (Kafka-compatible broker)
- **Resilience4j** (retry, circuit breaker), **nginx** (gateway)

---

## Not yet built / next steps

- **Notification service** (a second consumer of the transfer events)

