# Stream Chat Platform

Real-time chat system for live streaming. Built with Spring Boot backend and React frontend.
Similar to Twitch/YouTube Live chat with moderation, WebSocket support, and JWT auth.

## Features

- 💬 Real-time messaging via WebSocket (STOMP)
- 🔐 JWT authentication
- 🛡️ Moderation tools (timeout, ban, delete messages)
- ⚡ Rate limiting per user role
- 🎭 User roles (Broadcaster, Moderator, VIP, Subscriber)
- 📊 Chat modes (slow mode, followers-only, subscribers-only)
- 🚀 Scalable with Redis pub/sub
- 📝 Message history & replay for reconnects (2.5)
- 🔍 Audit logging for all moderation actions
- 🛡️ Optimistic locking with `@Version` for concurrent safety (2.4)

## Version History

### 2.4 — Moderation Concurrency
- `@Version` optimistic locking on `BannedUser` and `TimedOutUser`
- Flyway V9 migration adding `version BIGINT` columns with backfill
- Idempotent upserts in `banUser`/`timeoutUser` via `DataIntegrityViolationException` race guard
- Idempotent unbans (`unbanUser`/`removeTimeout`) — silent success if already-unbanned
- Optimistic lock retry-once pattern; `ConflictException`(409) on exhaustion
- Role cache eviction (`streamAuthorizationService.evictRoleCache()`) fires after DB commit

### 2.5 — Gap Replay
- `GET /api/streams/{id}/messages/replay?after={afterId}` — fetch missed messages on reconnect
- Backend cursor pagination filters by `redisSequenceId > afterId`
- Frontend tracks `lastSeenMessageId` and merges replayed messages via deduplication

### R2 — Docker & Environment Hardening
- Frontend WS URL derives from `window.location` if `VITE_WS_URL` unset (fail-fast only on SSR)
- Postgres uses strict env vars `${POSTGRES_USER:?must be set}` and `${POSTGRES_PASSWORD:?must be set}`
- Backend `DATABASE_USERNAME`/`DATABASE_PASSWORD` mapped to same Postgres env vars
- Healthcheck uses `curl` (Amazon Corretto image now includes curl via `microdnf`)

### 2.6b — Read-path N+1 Closure
- `UserBadgeRepository.findBadgeTypesByUserIdAndStreamIdOrGlobalIn()` — batched badge query
- `UserStreamRoleRepository.findByUserIdAndStreamIdIn()` — batched roles query

## Quick Start

### Backend (Spring Boot)

```bash
mvn spring-boot:run
```

Runs on `http://localhost:8080` with PostgreSQL and Redis (see Docker section below).

### Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173` and connects to backend at `http://localhost:8080/api`.

### Docker (Full Stack)

The fastest way to run the full stack (Spring Boot backend, React frontend, PostgreSQL, Redis).

#### Prerequisites

- Docker Engine 24+ and Docker Compose **v2** (`docker compose`). Legacy `docker-compose` v1 is not supported.

#### Configuration

1. Create the backend environment file (runtime settings for the API container):

   ```bash
   cp .env.example .env
   ```

2. Generate a `JWT_SECRET` and put it in `.env`:

   ```bash
   openssl rand -base64 48
   ```

   `JWT_SECRET` is **mandatory**. Compose refuses to start without it (`${JWT_SECRET:?JWT_SECRET must be set}` in `docker-compose.yml`), and the backend additionally refuses to boot if it is shorter than 32 bytes.

3. The frontend environment file is baked into the image at build time:

   ```bash
   cp frontend/.env.example frontend/.env
   ```

   Required variables: `VITE_API_URL` and `VITE_WS_URL`. If `VITE_WS_URL` is unset, the client derives from `window.location.protocol/host` (development only).

#### Required `.env` variables

| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | **Yes** | JWT signing key, ≥ 32 bytes. Compose refuses to start without it. |
| `POSTGRES_USER` | **Yes** | Must match `DATABASE_USERNAME` in the backend config. |
| `POSTGRES_PASSWORD` | **Yes** | Must match `DATABASE_PASSWORD` in the backend config. |
| `JWT_EXPIRATION` | No | Access-token lifetime in ms (legacy). Default `86400000`. |
| `JWT_ACCESS_TOKEN_TTL` | No | Access-token TTL. Default `PT15M`. |
| `JWT_REFRESH_TOKEN_TTL` | No | Refresh-token TTL. Default `P30D`. |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated browser origins allowed to call the API. Defaults to `http://localhost:3000,http://localhost:8080`. |
| `SPRING_PROFILES_ACTIVE` | No | Spring profile. `prod` is the Docker default. |
| `CHAT_RETENTION_DAYS` | No | Message retention window for cleanup. Default `90`. |

> **Postgres credentials must match** between `docker-compose.yml` env vars and `.env`/`DATABASE_USERNAME`/`DATABASE_PASSWORD`. Mismatch causes backend connection failure.

`DATABASE_URL`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `SERVER_PORT`, `AUTOMOD_*`, and `LOG_FORMAT` are accepted by the app but not consumed by the Docker runtime (compose sets the DB/Redis connection env vars itself).

#### Start

```bash
docker compose up --build -d
```

Foreground variant (watch logs in the terminal): `docker compose up --build`

#### Service map

| Service | Container | Port | URL |
|---|---|---|---|
| Backend API | `stream-chat-app` | 8080 | http://localhost:8080 |
| Backend health | `stream-chat-app` | — | http://localhost:8080/actuator/health |
| Frontend | `stream-chat-frontend` | 3000 | http://localhost:3000 |
| PostgreSQL | `stream-chat-postgres` | 5432 | `localhost:5432` (internal) |
| Redis | `stream-chat-redis` | 6379 | `localhost:6379` (internal) |

The backend starts only after Postgres and Redis report healthy; first start runs Flyway migrations automatically.

#### Verify

1. Health check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.
2. Open http://localhost:3000.
3. Register a user, then open the same stream in **two browser tabs** and send a message in one — it should appear in the other in real time (WebSocket/STOMP over `/ws-chat`).

#### Full test suite

With Docker running, all tests execute including the Testcontainers-gated integration tests:

```bash
mvn clean test          # backend — all tests
cd frontend && npm test # frontend
```

### 252 tests across unit, controller, and integration layers.

### Testing

Backend tests with Maven:

```bash
mvn test
```

Frontend tests:

```bash
cd frontend && npm test
```

### License

MIT