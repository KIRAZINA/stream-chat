# Stream Chat Platform

Production-ready Spring Boot backend for real-time stream chat. Supports WebSocket messaging, JWT auth, moderation, rate limiting, emotes, horizontal scaling via Redis, audit logging, pinned messages, and idempotent publishing.

## Features

- Real-time messaging via STOMP over SockJS
- JWT authentication with token refresh
- Moderation: timeout, ban, unban, message deletion, pin/unpin
- Rate limiting per role (regular 20/min, subscriber 50/min, mod/broadcaster 100/min)
- Chat modes: slow mode, subscribers-only, followers-only, emote-only
- AutoMod with spam detection, shadow banning, trust scores
- Horizontal scaling via Redis pub/sub
- Idempotent message publishing (deduplication by idempotency key)
- Message replay endpoint for reconnect recovery
- Audit logging for all moderation actions
- Automated data retention cleanup (messages 90 days, audit logs 365 days)
- Pinned messages for announcements
- User reputation system (schema ready)
- Prometheus metrics + health checks

## Tech Stack

- Java 17, Spring Boot 3.2.1
- PostgreSQL, Redis, Flyway
- JJWT 0.12, MapStruct, Lombok
- Micrometer (Prometheus), Logback structured logging
- Testcontainers for integration tests

## Quick Start

### Dev (H2 in-memory)

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

Chat client: `http://localhost:8080/chat.html`
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Docker Compose (PostgreSQL + Redis)

```bash
cp .env.example .env   # edit values as needed
docker-compose up -d
```

## Configuration

Copy `.env.example` to `.env` and fill in values. Key variables:

| Variable | Required | Description |
|---|---|---|
| `JWT_SECRET` | Yes | HMAC signing key, 64+ characters |
| `DATABASE_URL` | prod | `jdbc:postgresql://host:5432/dbname` |
| `DATABASE_USERNAME` | prod | DB username |
| `DATABASE_PASSWORD` | prod | DB password |
| `REDIS_HOST` | prod | Redis host |
| `CORS_ALLOWED_ORIGINS` | Optional | Comma-separated origins |

Spring profiles: `dev` (H2), `prod` (PostgreSQL + Redis via env vars).

## API

### Auth

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register user, returns JWT |
| POST | `/api/auth/login` | Login, returns JWT |
| POST | `/api/auth/refresh` | Refresh token (authenticated) |

### Streams

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/streams` | — | List live streams |
| GET | `/api/streams/{key}` | — | Stream details |
| GET | `/api/streams/{key}/messages` | — | Chat history (pagination, cursor) |
| GET | `/api/streams/{key}/messages/replay` | — | Replay window for reconnect |
| GET | `/api/streams/{key}/presence` | — | Active viewer count |
| POST | `/api/streams` | Yes | Create stream |
| PUT | `/api/streams/{key}` | Yes | Update stream |
| POST | `/api/streams/{key}/start` | Yes | Go live |
| POST | `/api/streams/{key}/stop` | Yes | End stream |
| DELETE | `/api/streams/{key}` | Yes | Delete stream |

### Settings

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/streams/{key}/settings` | Yes | Get chat settings |
| PUT | `/api/streams/{key}/settings` | Yes | Update settings |

### Moderation

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/streams/{key}/moderate/timeout` | Mod | Timeout user |
| POST | `/api/streams/{key}/moderate/ban` | Mod | Ban user |
| DELETE | `/api/streams/{key}/moderate/ban/{userId}` | Mod | Unban user |
| DELETE | `/api/streams/{key}/moderate/messages/{msgId}` | Mod | Delete message |
| DELETE | `/api/streams/{key}/moderate/messages/user/{userId}` | Mod | Delete all messages by user |
| POST | `/api/streams/{key}/moderate/pin` | Mod | Pin/unpin message |
| POST | `/api/streams/{key}/moderate/shadow-ban/{userId}` | Mod | Enable shadow ban |
| DELETE | `/api/streams/{key}/moderate/shadow-ban/{userId}` | Mod | Disable shadow ban |
| GET | `/api/streams/{key}/moderate/audit-logs` | Mod | Audit log |
| GET | `/api/streams/{key}/moderate/trust-score/{userId}` | Mod | User trust score |
| GET | `/api/streams/{key}/moderate/moderators` | Mod | List moderators |
| POST | `/api/streams/{key}/moderate/moderators` | Admin | Add moderator |
| DELETE | `/api/streams/{key}/moderate/moderators/{userId}` | Admin | Remove moderator |

### WebSocket (STOMP over SockJS)

Endpoint: `/ws-chat`

Send with header `Authorization: Bearer <JWT>` on CONNECT.

| Direction | Destination | Description |
|---|---|---|
| Client → Server | `/app/chat.send/{streamKey}` | Send message (body: `ChatMessageDTO`) |
| Client → Server | `/app/chat.join/{streamKey}` | Join stream |
| Client → Server | `/app/chat.leave/{streamKey}` | Leave stream |
| Client → Server | `/app/chat.moderate/{streamKey}` | Moderate action |
| Server → Clients | `/topic/stream/{streamKey}` | New message |
| Server → Clients | `/topic/stream/{streamKey}/events` | Join/leave events |
| Server → Clients | `/topic/stream/{streamKey}/moderation` | Moderation events |
| Server → User | `/user/queue/errors` | Error messages |

## Database Migrations

Flyway manages schema. Current migrations:

| Version | Description |
|---|---|
| V1 | Initial schema (users, streams, messages, roles) |
| V2 | Moderation (bans, timeouts, moderation logs) |
| V3 | Stream settings (slow mode, sub-only, etc.) |
| V4 | User roles per stream |
| V5 | Reply-to-message, indexes |
| V6 | Phase 2 features (AutoMod, emotes, badges) |
| V7 | Phase 3: audit logs, pinned messages, idempotency keys, reputation |

## Testing

```bash
mvn clean test          # all tests
mvn test -Dtest=ChatServiceTest  # single class
```

203 tests across unit, controller, and integration layers.

## Deployment

1. Copy `.env.example` to `.env`, set production values
2. `docker-compose up -d`
3. Health check: `curl http://localhost:8080/actuator/health`
4. Metrics: `/actuator/prometheus`

## Project Structure

```
src/main/java/com/streamchat/
  config/          Spring configuration
  controller/      REST + WebSocket controllers
  exception/       Error handling
  listener/        Redis subscriber, presence listener
  model/           DTOs, entities, enums
  repository/      Spring Data JPA repositories
  scheduled/       Cron cleanup tasks
  security/        JWT filter, token provider
  service/         Business logic
```
