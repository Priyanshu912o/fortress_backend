# FORTRESS Chat Backend

A WhatsApp-inspired messaging backend built with **Java 17 / Spring Boot 3**, designed to demonstrate backend engineering skills: layered MVC architecture, relational data modeling with PostgreSQL, async messaging with Apache Kafka, a hand-rolled token-bucket rate limiter, and real-time WebSocket delivery.

> **Portfolio project** — every component is real and runnable, scoped to be defensible in a technical interview.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Flutter App (Client)                     │
│              Firebase Auth · REST · WebSocket                │
└──────────┬──────────────────────┬────────────────────────────┘
           │ REST (HTTP)          │ WebSocket (WS)
           ▼                      ▼
┌──────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                    │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │  Controllers  │  │   Security   │  │   WebSocket Layer  │  │
│  │  (REST API)   │  │  Firebase    │  │   FortressWS       │  │
│  │              │  │  Auth Filter  │  │   Handler +        │  │
│  │  User        │  │              │  │   RoomManager      │  │
│  │  Connection  │  └──────────────┘  └────────────────────┘  │
│  │  ChatRoom    │                                             │
│  │  Message     │  ┌──────────────┐  ┌────────────────────┐  │
│  └──────┬───────┘  │  Rate Limiter │  │   Kafka Pipeline   │  │
│         │          │  TokenBucket   │  │   Producer →       │  │
│         ▼          │  (hand-rolled) │  │   Consumer         │  │
│  ┌──────────────┐  └──────────────┘  └────────────────────┘  │
│  │   Services    │                                            │
│  │  (Business    │                                            │
│  │   Logic)      │                                            │
│  └──────┬───────┘                                             │
│         ▼                                                     │
│  ┌──────────────┐                                             │
│  │ Repositories  │  Spring Data JPA                           │
│  └──────┬───────┘                                             │
└─────────┼─────────────────────────────────────────────────────┘
          ▼
┌──────────────┐   ┌──────────────┐
│  PostgreSQL   │   │    Kafka     │
│  (Durable     │   │  (Async     │
│   State)      │   │   Delivery)  │
└──────────────┘   └──────────────┘
```

### Layered MVC (one-way dependencies)

```
Controller → Service → Repository → PostgreSQL
     ↓
  DTO mapping, HTTP concerns, request validation
```

No cross-layer shortcuts. Controllers never touch repositories directly.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17+, Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| Async Messaging | Apache Kafka (KRaft, no ZooKeeper) |
| Auth | Firebase Admin SDK (ID token verification) |
| Real-time | Raw WebSocket (`/ws`) |
| Rate Limiting | Hand-rolled token-bucket (no library) |
| Build | Maven |
| Containerization | Docker + Docker Compose |

## Quick Start

### Prerequisites
- Java 17+ (`brew install openjdk`)
- Maven (`brew install maven`)
- Docker Desktop (`brew install --cask docker`)

### Run with Docker Compose

```bash
# Start everything (Postgres + Kafka + App)
docker compose up --build

# Health check
curl http://localhost:8080/health
```

### Run locally (development)

```bash
# Start only Postgres and Kafka
docker compose up postgres kafka -d

# Build and run the app
export DEV_BYPASS_AUTH=true   # Enable mock tokens for testing
mvn clean package -DskipTests
java -jar target/fortress-chat-1.0.0.jar
```

## API Reference

All endpoints require `Authorization: Bearer <firebase-id-token>` header (except `/health`).

### Users

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/users/sync` | Upsert user profile on login (rate-limited per-IP) |
| `GET` | `/api/users/search?email=xxx` | Search users by email |
| `GET` | `/api/users/{uid}` | Get user profile |

### Connections (Friend Requests)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/connections` | Send connection request |
| `GET` | `/api/connections` | List accepted contacts |
| `GET` | `/api/connections/pending` | Incoming pending requests |
| `GET` | `/api/connections/sent` | Outgoing sent requests |
| `PATCH` | `/api/connections/{id}/accept` | Accept request (auto-creates chat room) |
| `PATCH` | `/api/connections/{id}/reject` | Reject request |
| `PATCH` | `/api/connections/{id}/block` | Block user |

### Chat Rooms

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/chat-rooms` | List user's chat rooms |
| `POST` | `/api/chat-rooms/direct` | Get/create 1:1 chat room |
| `POST` | `/api/chat-rooms/group` | Create group chat room |
| `GET` | `/api/chat-rooms/{id}` | Get chat room details |

### Messages

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/messages` | Send message (rate-limited per-user, publishes to Kafka) |
| `GET` | `/api/messages/{chatRoomId}?cursor=&limit=` | Paginated message history |
| `POST` | `/api/messages/{id}/delivered` | Mark message delivered |
| `POST` | `/api/messages/read` | Mark all messages in room as read |

### WebSocket

Connect to `ws://localhost:8080/ws`, then authenticate:

```json
→ { "type": "auth", "token": "<firebase-id-token>" }
← { "type": "authenticated", "uid": "..." }
```

**Client → Server events:** `send_message`, `mark_read`, `typing`
**Server → Client events:** `authenticated`, `message_new`, `message_ack`, `message_delivered`, `message_read`, `presence`, `connection_request`, `error`

## Design Decisions

### PostgreSQL vs Kafka — Data Ownership

**PostgreSQL owns durable state** that needs to be queried and joined:
- User accounts, connection relationships, chatroom membership
- Message history (a reconnecting client fetches from DB, not Kafka)
- Read/delivery receipts

**Kafka owns transient delivery** — getting a message to connected clients right now:
- Message ingestion is decoupled from delivery processing
- A burst of sends doesn't block the REST request path
- The consumer marks messages as delivered asynchronously

If a client reconnects after being offline, it fetches unread history via paginated DB query, not by replaying Kafka.

### Rate Limiter — Token Bucket

Implemented from scratch (`TokenBucket.java`) — no external library:

- **Per-user bucket** on `POST /api/messages`: 10 tokens max, refills 1/sec. Prevents chatroom flooding.
- **Per-IP bucket** on `POST /api/users/sync`: 5 tokens max, refills 1/10sec. Mitigates automated abuse.
- Buckets stored in `ConcurrentHashMap` (in-memory). Stale buckets evicted every 10 minutes.
- Returns `429 Too Many Requests` with `Retry-After` header on exhaustion.

**Production note:** A distributed deployment would replace the `ConcurrentHashMap` with Redis. Not built now — documented as a next step.

### Client Message Deduplication

Messages use a `clientMessageId` (client-generated UUID) with a unique constraint. If the client retries a failed send, the same `clientMessageId` triggers an idempotent upsert — no duplicate messages.

## Data Model

```
users (uid PK)
  ├── connections (from_uid, to_uid, status)
  ├── chat_room_participants (uid, chat_room_id, role, unread_count)
  │     └── chat_rooms (id, is_direct, group_name, last_message_*)
  ├── messages (id, chat_room_id, sender_id, client_message_id, body)
  └── message_receipts (message_id, uid, delivered_at, read_at)
```

## What I'd Do Next

- **Redis** for distributed rate limiting (replace in-memory `ConcurrentHashMap`)
- **Push notifications** via Firebase Cloud Messaging in the Kafka consumer
- **Message pagination optimization** with keyset pagination instead of timestamp cursor
- **E2E encryption** with Signal Protocol
- **File/media messages** with S3-compatible object storage
- **Read receipts in Kafka** — separate topic for receipt events
- **Load testing** with k6 or Gatling
