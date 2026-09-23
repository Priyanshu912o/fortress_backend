# FORTRESS Backend — Project Brief

## Intent

FORTRESS Backend is a WhatsApp-inspired messaging backend, built in Java/Spring Boot,
that serves as the server-side counterpart to an existing Flutter messaging app
(FORTRESS). The Flutter app currently talks to Firebase directly; this project is a
standalone backend built to demonstrate backend engineering skills — layered
architecture, relational data modeling, async messaging, and a hand-rolled rate
limiter — independent of whether it's ever wired back into the Flutter client.

**This is a portfolio/interview project, not production infrastructure.** Every
component should be real and runnable, but scoped to be defensible in a technical
interview — someone should be able to ask "walk me through what happens when a
message is sent" and get a clear, accurate answer straight from the code.

## Goals (in priority order)

1. A working Java/Spring Boot service with a genuine layered MVC architecture
   (controller → service → repository), not a single fat controller.
2. PostgreSQL used for what it's actually good at: user accounts, chatroom
   membership, and durable message history — with schema decisions that have
   clear reasoning behind them (see Data Model below).
3. A token-bucket rate limiter, implemented from scratch (no off-the-shelf
   rate-limit library), applied to two endpoints with two different intents:
   - `POST /messages` — capped per-user, to prevent spam/flooding in a chatroom
   - `POST /auth/login` — capped per-IP or per-account, to mitigate brute-force
     login attempts
4. An async message pipeline via Apache Kafka: message ingestion (producer) is
   decoupled from delivery/notification (consumer), so a burst of sends doesn't
   block the request path.
5. Dockerized, Maven-built, runnable locally in a staging-like setup
   (`docker-compose up` should bring up the app + Postgres + Kafka together).

## Explicit non-goals (keep scope tight)

- No real-time WebSocket delivery layer for this phase — REST + Kafka is enough
  to demonstrate the async pattern. WebSocket push can be a documented "next step"
  in the README rather than built now.
- No object storage / multimedia messages. Text messages only.
- No API gateway or load balancer layer — single Spring Boot service is fine.
- No integration with the Flutter FORTRESS client. This is a standalone backend.
- No production-grade auth (OAuth providers, etc.) — a simple username/password
  + JWT is sufficient; the login endpoint exists primarily as a second target for
  the rate limiter, not as a security showcase.

## Tech stack

- **Language/Framework:** Java 17+, Spring Boot (Web, Data JPA, Validation)
- **Database:** PostgreSQL
- **Async messaging:** Apache Kafka
- **Build tool:** Maven
- **Containerization:** Docker + docker-compose (app, Postgres, Kafka in one file)
- **Auth:** Spring Security + JWT (minimal — just enough to gate endpoints)

## Architecture

Standard layered MVC, one direction of dependency only (controller depends on
service, service depends on repository — never the reverse):

```
Controller layer   → request validation, DTO mapping, HTTP concerns only
Service layer       → business logic (rate limiting, message routing, auth)
Repository layer    → Spring Data JPA repositories, PostgreSQL-backed
```

The rate limiter should live in its own component (e.g. a `RateLimiterService`
or a servlet filter/interceptor), not embedded inline in the controller — it
needs to be a reusable piece applied to more than one endpoint.

## Data model (PostgreSQL)

Four core tables, chosen because each maps to something the app actually needs
to query relationally (not just store):

- **users** — id, username, password_hash, created_at
- **chatrooms** — id, type (direct / group), name (nullable for direct), created_at
- **chatroom_members** — chatroom_id, user_id, joined_at (many-to-many join table;
  this is *why* it's relational — membership is a set of relationships, not a
  document)
- **messages** — id, chatroom_id, sender_id, content, sent_at, delivered (bool)

Design reasoning to preserve in code comments / README: PostgreSQL owns
*durable state that needs to be queried and joined* (who's in this chatroom,
what's the message history for a reconnecting client). Kafka owns *transient
delivery* (getting a message to an already-connected client right now). If a
client reconnects after being offline, it should be able to fetch unread
history via a paginated query against `messages`, not by replaying Kafka.

## API surface (MVP)

- `POST /auth/register` — create user
- `POST /auth/login` — authenticate, issue JWT (rate-limited)
- `POST /chatrooms` — create a direct or group chatroom
- `GET /chatrooms/{id}/messages?after={cursor}` — paginated message history
- `POST /messages` — send a message (rate-limited; publishes to Kafka on success)

## Rate limiter — implementation notes

Token-bucket, implemented manually (this is the point — it should be explainable
line-by-line, not "I used a library"):

- Each user gets a bucket with a max capacity and a refill rate (e.g. 10 tokens,
  refilling 1/sec) for `POST /messages`.
- Each IP or account gets a stricter bucket (e.g. 5 tokens, refilling 1 per
  10 sec) for `POST /auth/login`.
- On limit exceeded, return `429 Too Many Requests`.
- Keep bucket state in-memory (a `ConcurrentHashMap<userId, Bucket>`) for this
  phase — note in the README that a distributed deployment would need Redis
  instead, but don't build that now.

## Kafka pipeline

- Producer: on successful `POST /messages`, after the message is persisted to
  PostgreSQL, publish a `MessageSentEvent` to a `messages` topic.
- Consumer: a separate consumer component reads from `messages` and simulates
  delivery (log + mark `delivered = true` in the DB). This is intentionally
  simple — the point is demonstrating producer/consumer decoupling, not
  building a full notification system.

## Milestones (suggested build order)

1. Spring Boot project scaffold + Docker Compose (app, Postgres, Kafka) —
   verify all three containers start and talk to each other.
2. Data model + repositories + basic CRUD for users/chatrooms/messages.
3. Auth (register/login + JWT), with login rate limiter wired in.
4. Message-send endpoint with rate limiter wired in.
5. Kafka producer on message-send; consumer that marks messages delivered.
6. Paginated message-history endpoint (cursor-based, to match the existing
   FORTRESS Flutter app's pagination pattern).
7. README with setup instructions, architecture diagram (even ASCII is fine),
   and the design-reasoning notes above written up properly.

## Definition of done

Every bullet below should be demonstrably true by inspecting the running code —
these are the exact claims that will be made about this project in interviews,
so the implementation needs to actually back them up:

- [ ] Layered MVC architecture with no cross-layer shortcuts
- [ ] PostgreSQL persisting users, chatroom membership, and message history
- [ ] Token-bucket rate limiter applied to `POST /messages` and `POST /auth/login`
- [ ] Kafka producer/consumer decoupling message ingestion from delivery
- [ ] Full stack runs via `docker-compose up`, built with Maven
- [ ] README documents the Postgres-vs-Kafka reasoning and rate limiter design