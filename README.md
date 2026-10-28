# Stream Chat Platform

Real-time chat system for live streaming. Spring Boot backend + React (Vite) frontend.
Similar to Twitch/YouTube Live chat with moderation, WebSocket support, JWT auth, and multi-platform aggregation.

## Features

- Real-time messaging via WebSocket (STOMP) with Redis pub/sub fan-out
- JWT authentication (access + refresh tokens) with BCrypt password hashing
- Moderation tools (timeout, ban, delete messages, audit logging)
- Per-user/per-stream rate limiting (application + Nginx L7)
- User roles (Broadcaster, Moderator, VIP, Subscriber)
- Chat modes (slow mode, followers-only, subscribers-only)
- Message history & replay for reconnects
- Custom emotes with CDN caching headers
- AI-powered AutoMod (caps, spam, toxicity, shadow-ban)
- Distributed tracing (Micrometer Tracing + Brave, W3C trace context)
- Stream analytics (MPS, top emotes, unique chatters, time-series)
- Multi-platform chat aggregator (Twitch/YouTube/Kick webhooks)
- Kubernetes manifests with HPA and actuator probes
- Nginx reverse proxy with TLS and rate limiting

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.1 |
| Database | PostgreSQL (prod) / H2 (dev) |
| ORM | JPA / Hibernate |
| Migrations | Flyway (V1-V9) |
| Cache | Caffeine (in-memory) + Redis (distributed) |
| Messaging | STOMP over WebSocket + Redis pub/sub |
| Auth | JWT (JJWT 0.12.5) + Spring Security |
| Tracing | Micrometer Tracing (Brave bridge) |
| Metrics | Micrometer + Prometheus + Actuator |
| Build | Maven |
| Frontend | React + Vite (Node 20) |
| Infra | Docker, Docker Compose, Nginx, Kubernetes |

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+
- Node.js 20+ (for frontend)
- Docker & Docker Compose (for full stack)

### Run Backend (dev profile)
```bash
mvn spring-boot:run
```
Default profile uses H2 in-memory DB, no Redis. H2 console disabled by default.

### Run Full Stack (Docker)
```bash
docker compose up --build
```
Activates `prod` profile (PostgreSQL + Redis). Configure `.env` for secrets.

### Frontend
```bash
cd frontend
npm ci
npm run dev
```

## Project Structure

```
src/main/java/com/streamchat/
  aggregator/       # Multi-platform chat ingestion
  config/           # Spring config, security, cache, websocket, tracing
  controller/       # REST + WebSocket + analytics + emote controllers
  exception/        # GlobalExceptionHandler, ErrorResponse
  listener/         # Redis pub/sub subscribers
  model/            # Entities, DTOs, enums
  repository/       # Spring Data JPA
  security/         # JWT, sanitization
  service/          # Business logic
  scheduled/        # Scheduled tasks
deploy/
  nginx/            # Nginx reverse proxy config
  k8s/              # Kubernetes manifests
  stream-affinity-lb.md
frontend/           # React/Vite frontend
.github/workflows/  # CI pipelines
```

## Configuration

Key properties (`src/main/resources/application.properties`):

- `jwt.secret` — must be >= 32 bytes (fail-fast at startup)
- `app.cors.allowed-origins` — comma-separated origins
- `app.automod.*` — caps, spam, toxicity thresholds, shadow-ban, AI inference
- `app.cache.*` — Caffeine TTL/maxSize per domain
- `app.aggregator.*` — enabled, persist-external-messages
- `chat.broadcast.local-first` — local-first delivery toggle
- `chat.slowmode.storage` — memory or redis
- `management.tracing.sampling.probability` — tracing sample rate

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/register` | Register |
| POST | `/api/auth/refresh` | Refresh token |
| GET | `/api/streams` | List streams |
| POST | `/api/streams` | Create stream |
| GET | `/api/streams/{key}` | Get stream |
| GET | `/api/streams/{key}/messages` | Message history |
| POST | `/api/streams/{key}/messages` | Send message (REST) |
| GET | `/api/streams/{key}/messages/replay` | Replay missed messages |
| GET | `/api/streams/{key}/presence` | Presence count |
| POST | `/api/streams/{key}/moderate/**` | Moderation actions |
| GET/PUT | `/api/streams/{key}/settings` | Stream settings |
| GET | `/api/streams/{key}/emotes` | Stream emotes |
| GET | `/api/emotes/global` | Global emotes |
| GET | `/api/streams/{key}/analytics/summary` | Analytics summary |
| GET | `/api/streams/{key}/analytics/time-series` | Analytics time-series |
| POST | `/api/streams/{key}/aggregator/webhook/{platform}` | External chat ingestion |
| WS | `/ws-chat/**` | STOMP WebSocket |

## WebSocket

- Endpoint: `/ws-chat` (SockJS) or `/ws-chat/stream/{streamKey}` (affinity)
- Application prefix: `/app`
- Topics: `/topic/stream/{streamKey}`, `/user/queue/errors`

## Testing

```bash
mvn clean test
```

360+ tests across unit and integration (Testcontainers).

## Deployment

- Docker Compose: `docker compose up`
- Kubernetes: `kubectl apply -f deploy/k8s/`
- Nginx reverse proxy: `deploy/nginx/`

## License

See LICENSE file.
