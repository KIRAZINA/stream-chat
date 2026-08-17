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

### 2.7 — Stream Affinity, Observability, Wire Fixes & Char-Count Enforcement
- **Stream-keyed WebSocket endpoint (A3)**: `stomp-client.ts` can connect to `/ws-chat/stream/{streamKey}`
  (`VITE_USE_STREAM_AFFINITY=true`) so the stream key is carried in the path for consistent-hash
  load balancing; `StreamAffinityHandshakeInterceptor` resolves it server-side. Default SockJS
  `/ws-chat` path preserved when affinity is off.
- **`replyToMessageId` wire field**: normalized to the camelCase name the frontend sends; the
  STOMP reply path no longer drops the field on the wire.
- **Local-first broadcast**: `chat.broadcast.local-first` delivers WS sends directly through the
  in-memory broker when stream affinity pins the sender to the owner instance (`forceRedis` keeps
  REST sends on the Redis fan-out path).
- **Consistent-hash load balancing (A4)**: `deploy/stream-affinity-lb.md` documents the nginx +
  Envoy routing + rollback for instance pinning.
- **Broadcast observability (A5)**: `MetricsService` records local-delivery vs. orphaned broadcasts
  (`chat.broadcast.local`, `chat.broadcast.orphaned`); Prometheus smoke test validates the gauges.
- **Character-count enforcement (works)**: `maxMessageLength` is enforced server-side in
  `ChatService.validateMessageContent` and surfaced live on the frontend `MessageInput` counter;
  over-limit sends are rejected with `400 Bad Request`.
- **Slow mode**: enforced in `ChatService.enforceSlowMode` via an atomic Lua GET+SET against Redis
  (cross-instance) with an in-process Caffeine fallback. The fallback is what the integration tests
  exercise; see *Current State Notes* below.

### 2.4 — Moderation Concurrency
- `@Version` optimistic locking on `BannedUser` and `TimedOutUser`
- Flyway V9 migration adding `version BIGINT` columns with backfill
- Idempotent upserts in `banUser`/`timeoutUser` via `DataIntegrityViolationException` race guard
- Idempotent unbans (`unbanUser`/`removeTimeout`) — silent success if already-unbanned
- Optimistic lock retry-once pattern; `ConflictException`(409) on exhaustion
- Role cache eviction (`streamAuthorizationService.evictRoleCache()`) fires after DB commit

### 2.5 — Gap Replay
- `GET /api/streams/{streamKey}/messages/replay?afterSequenceId={id}&limit={n}` — fetch missed messages on reconnect
- Backend cursor pagination; the replay endpoint reuses the history query (sequence-based trimming is best-effort for now)
- Frontend tracks `lastSeenMessageId` and merges replayed messages via deduplication

### R5 — Chat Connection & Theme Fixes
- **Chat hanging ("Connecting to chat… forever")**: `useStompChat.ts` derived `ws://localhost:3000` from `window.location` when `VITE_WS_URL` was unset (Docker build excludes `.env` files). SockJS requires `http://` — `ws://` causes indefinite hang. Fixed `buildSockJSUrl()` in `stomp-client.ts` to convert `ws://` → `http://` and `wss://` → `https://`
- **Theme stuck on dark**: `tailwind.config.js` had no `darkMode` setting — Tailwind defaulted to `'media'` mode, ignoring the `dark` class toggled by `DarkModeToggle`. Fixed: added `darkMode: 'class'`
- **Theme styling**: `index.css` hardcoded `color-scheme: dark` and a dark `body` gradient. `ChatWindow.tsx` and `StreamSettingsForm.tsx` used hardcoded dark classes without `dark:` prefix. Fixed: all components now use `dark:` prefixes for dark-only styles, light defaults for light mode
- **Sidebar placeholder**: `StreamSettingsForm` only showed 3 settings with hardcoded dark styling and no error handling. Rewrote: 9 settings fields, loading/error states, proper validation, light/dark theme support

### R4 — Frontend API Proxy (Fix: Registration Failed)
- `frontend/.dockerignore` excluded `.env` but not `.env.production`, which had `VITE_API_URL=https://api.yourdomain.com/api` baked into the frontend at Docker build time — causing all API calls to hit a non-existent domain
- Fix: Added `.env.production` and `.env.development` to `.dockerignore` so Vite falls back to relative `/api`
- Fix: Added nginx reverse proxies for `/api/` → backend:8080 and `/ws-chat/` → backend:8080 WebSocket
- Frontend now uses relative URLs; nginx routes to the `stream-chat-app` container on the Docker network

### R3 — ChatController & Docker Startup Automation
- Implemented `ChatController` with `POST /api/streams/{streamKey}/messages` endpoint
  (`GET /messages` and `GET /presence` already handled by `StreamController`)
- Pre-populated `.env` with all required values including a secure 256-bit `JWT_SECRET`
- Backend profile defaults to `prod` in Docker (`-P !dev` excludes H2 driver)
- Postgres `POSTGRES_DB` sourced from `${POSTGRES_DB}` for consistency with `DATABASE_URL`
- Dockerfile uses `-Dmaven.test.skip=true` to exclude test compilation from production image

### R2 — Docker & Environment Hardening
- Frontend WS URL derives from `window.location` if `VITE_WS_URL` unset (fail-fast only on SSR)
- Postgres uses strict env vars `${POSTGRES_USER:?must be set}` and `${POSTGRES_PASSWORD:?must be set}`
- Backend `DATABASE_USERNAME`/`DATABASE_PASSWORD` mapped to same Postgres env vars
- Healthcheck uses `curl` (Amazon Corretto image now includes curl via `microdnf`)

### 2.6b — Read-path N+1 Closure
- `UserBadgeRepository.findBadgeTypesByUserIdAndStreamIdOrGlobalIn()` — batched badge query
- `UserStreamRoleRepository.findByUserIdAndStreamIdIn()` — batched roles query

## Current State Notes

- **Tests**: 315 total (295 backend + 20 frontend). 6 backend tests are Testcontainers-gated and skip when Docker is unavailable to the test runner (Redis Lua-script + multi-instance fan-out tests); all non-gated tests pass.
- **Slow mode**: functional and integration-tested (a second rapid message is rejected with HTTP 429 / a STOMP error on `/user/queue/errors`). The backend enforces it via the in-process Caffeine store by default; the Redis-backed Lua path is configured (`chat.slowmode.storage`) but the `RedisConfig` bean is gated by a `@ConditionalOnProperty` tied to `spring.autoconfigure.exclude`, so the Redis template is not created when Redis auto-configuration is excluded — slow mode therefore falls back to the in-process store. Wire up Redis to get cross-instance enforcement.
- **Char-count enforcement**: working — the server validates `maxMessageLength` and the frontend shows a live counter.
- **Gap replay**: the `/messages/replay` endpoint exists and the frontend deduplicates backfill, but sequence-based trimming is best-effort (the endpoint currently reuses the history query).

## Quick Start

### Backend (Spring Boot)

```bash
mvn spring-boot:run
```

Runs on `http://localhost:8080` with an H2 in-memory database and the in-process Caffeine
store (Redis is excluded from the default `dev` profile — see Docker for the full PostgreSQL +
Redis stack).

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

The `.env` file is pre-populated with all required variables. To use your own
values, copy the example and edit:

1. Backend environment file (runtime settings for the API container):

   ```bash
   cp .env.example .env
   ```

   All required variables are already set in the committed `.env`. Edit them
   as needed — the critical ones are:

   | Variable | Required | Purpose |
   |---|---|---|
   | `JWT_SECRET` | **Yes** | JWT signing key, ≥ 32 bytes. Compose refuses to start without it. |
   | `POSTGRES_USER` | **Yes** | Must match `DATABASE_USERNAME` in the backend config. |
   | `POSTGRES_PASSWORD` | **Yes** | Must match `DATABASE_PASSWORD` in the backend config. |
   | `POSTGRES_DB` | **Yes** | Database name. Must match the DB name in `DATABASE_URL`. |
   | `DATABASE_URL` | **Yes** | JDBC URL, e.g. `jdbc:postgresql://postgres:5432/<POSTGRES_DB>`. |
   | `REDIS_HOST` | No | Redis hostname. Docker default: `redis`. |
   | `REDIS_PORT` | No | Redis port. Default: `6379`. |
   | `JWT_EXPIRATION` | No | Access-token lifetime in ms. Default `86400000`. |
   | `JWT_ACCESS_TOKEN_TTL` | No | Access-token TTL. Default `PT15M`. |
   | `JWT_REFRESH_TOKEN_TTL` | No | Refresh-token TTL. Default `P30D`. |
   | `CORS_ALLOWED_ORIGINS` | No | Comma-separated browser origins allowed to call the API. Defaults to `http://localhost:3000,http://localhost:8080`. |
   | `SPRING_PROFILES_ACTIVE` | No | Spring profile. `prod` is the Docker default. |
   | `CHAT_RETENTION_DAYS` | No | Message retention window for cleanup. Default `90`. |
   | `AUTOMOD_*` | No | AutoMod thresholds (caps, spam, trust decay, shadow-ban). |
   | `LOG_FORMAT` | No | `TEXT` or `JSON`. Default `TEXT`. |

   > **Postgres credentials must match** between `docker-compose.yml` env vars
   > and `DATABASE_USERNAME`/`DATABASE_PASSWORD` in `.env`. Mismatch causes
   > backend connection failure.

2. Generate a `JWT_SECRET` if you want a different one:

   ```bash
   openssl rand -base64 32
   ```

   `JWT_SECRET` is **mandatory** — the backend refuses to boot if it is shorter
   than 32 bytes, and Compose refuses to start without it.

3. The frontend environment file is only needed for local development (`npm run
   dev`). In Docker, the frontend env vars are baked into the image at build
   time:

   ```bash
   cp frontend/.env.example frontend/.env
   ```

   Required variables: `VITE_API_URL` and `VITE_WS_URL`. If `VITE_WS_URL` is
   unset, the client derives from `window.location.protocol/host` (development
   only).

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

1. Health: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.
2. Open http://localhost:3000.
3. Register a user, then open the same stream in **two browser tabs** and send a message in one — it should appear in the other in real time (WebSocket/STOMP over `/ws-chat`).

API endpoints available on the backend:

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/streams/{streamKey}/messages` | Public | Paginated chat history (via `StreamController`) |
| `GET` | `/api/streams/{streamKey}/messages/replay` | Auth | Missed messages for reconnect recovery (via `StreamController`) |
| `POST` | `/api/streams/{streamKey}/messages` | Auth required | Send a chat message (via `ChatController`) |
| `GET` | `/api/streams/{streamKey}/presence` | Public | Active viewer count |
| `GET` | `/api/streams/{streamKey}/settings` | Auth | Stream chat settings (via `SettingsController`) |
| `PUT` | `/api/streams/{streamKey}/settings` | Auth (owner) | Update chat settings (via `SettingsController`) |
| `POST` | `/api/streams/{streamKey}/moderate/*` | Auth (moderator+) | Timeouts, bans, shadow-ban, pinning, logs (via `ModerationController`) |

#### Full test suite

With Docker available, the Testcontainers-gated integration tests run too:

```bash
mvn clean test          # backend — all tests
cd frontend && npm run test -- --run # frontend
```

**315 tests** (295 backend + 20 frontend); 6 backend tests are Testcontainers-gated and skip without Docker. All non-gated tests pass.

#### Testing

Run a subset of tests with Maven:

```bash
mvn test
```

Frontend tests (non-watch mode):

```bash
cd frontend && npm run test -- --run
```

### License

MIT