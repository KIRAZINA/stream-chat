# Stream Chat Platform

A Spring Boot (Java 17) backend for a real-time chat system designed for live streaming.

See [ROADMAP.md](ROADMAP.md) for the staged plan to turn the current project into a Twitch-like stream chat product.

## Features

- WebSocket-based real-time messaging
- Authentication and authorization (JWT)
- Moderation tools (timeout/ban/message deletion)
- Rate limiting / spam protection
- Custom emotes support
- Horizontal scaling via Redis pub/sub

## Tech Stack

- Java 17
- Spring Boot 3
- Maven
- PostgreSQL (runtime)
- Redis (cache/pub-sub)
- Flyway (database migrations)
- OpenAPI (springdoc)

## Prerequisites

- Java 17+
- Maven 3.9+ (or Maven Wrapper if you add it)

## Configuration

The app uses Spring profiles:

- `dev`: H2 in-memory database for local development (see `application-dev.properties`)
- `prod`: configuration via environment variables (see `application-prod.properties`)

By default no profile is selected. Use `--spring.profiles.active=dev` for local development or set `SPRING_PROFILES_ACTIVE=prod` for production.

### Environment variables (production)

- `JWT_SECRET` (required in `prod`) — **use a long secret (64+ chars)**
- `JWT_EXPIRATION` (optional, default `86400000`)

For `prod` profile:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT` (optional, default `6379`)
- `REDIS_PASSWORD` (optional)
- `CORS_ALLOWED_ORIGINS` (optional, comma-separated)

### CORS

Use `app.cors.allowed-origins` to control allowed origins.
If set to `*`, credentials are disabled. If set to a list, credentials are enabled.

## Frontend

The application includes a built-in chat client:

- **Main chat client**: `http://localhost:8080/chat.html` — Full-featured chat UI with login, stream join, chat history, reconnect, and moderation support
- **Legacy test page**: `http://localhost:8080/chat-test.html` — Simple WebSocket test page (deprecated)

### Chat Client Features

- **Authentication**: Login or register directly in the UI
- **Stream Join**: Enter a stream key to join the chat
- **Chat History**: Loads recent messages on join (paginated)
- **Reconnect**: Automatically reconnects on connection loss
- **Message Deletion**: Updates UI in real-time when messages are deleted
- **Moderator Actions**: Delete messages (if user is a moderator)
- **Badges**: Display user roles (broadcaster, moderator, subscriber, follower)
- **Emotes**: Structured emote rendering

## API Overview

### REST endpoints

- `POST /api/auth/register` — register and return JWT
- `POST /api/auth/login` — login and return JWT
- `POST /api/auth/refresh` — refresh JWT (requires authentication)

- `GET /api/streams` — list live streams
- `GET /api/streams/{streamKey}` — get stream details
- `POST /api/streams` — create stream (requires authentication)
- `POST /api/streams/{streamKey}/start` — start stream (requires authentication)
- `POST /api/streams/{streamKey}/stop` — stop stream (requires authentication)

Moderation and settings are under:

- `/api/streams/{streamKey}/moderate/**`
- `/api/streams/{streamKey}/settings`

### WebSocket (STOMP over SockJS)

- Endpoint: `/ws-chat`
- Application destinations (client -> server):
  - `/app/chat.send/{streamKey}`
  - `/app/chat.join/{streamKey}`
  - `/app/chat.leave/{streamKey}`
  - `/app/chat.moderate/{streamKey}`
- Topics (server -> clients):
  - `/topic/stream/{streamKey}`
  - `/topic/stream/{streamKey}/events`
  - `/topic/stream/{streamKey}/moderation`
- User queue (server -> specific user):
  - `/user/queue/errors`

WebSocket authentication is performed by sending a native STOMP header:

```text
Authorization: Bearer <JWT>
```

## Rate Limiting

The application enforces per-role rate limits to prevent spam:

- **Regular users**: 20 messages per 60 seconds
- **Subscribers**: 50 messages per 60 seconds  
- **Moderators/Broadcasters**: 100 messages per 60 seconds

Limits are tracked per stream per user and stored in Redis when available (or in-memory fallback).

When a user exceeds their rate limit, they receive a `RateLimitException` with a clear message.

## Known limitations / TODO

- None currently tracked.

## Testing

This project contains 3 levels of tests:

- **Unit tests (services)** — `src/test/java/com/streamchat/service`
  - Fast tests using Mockito (no Spring context).
- **Controller tests (`@WebMvcTest`)** — `src/test/java/com/streamchat/controller`
  - Validate REST contracts (status codes, JSON shape) with mocked dependencies.
- **Integration tests (`@SpringBootTest`)** — `src/test/java/com/streamchat/integration`
  - Run against a real Spring context.
  - Use `dev` profile (H2 + `create-drop`, Redis auto-config excluded).
  - Moderation integration tests focus on HTTP + security flow.

### Run all tests

```bash
mvn test
```

### Run a specific test class

```bash
mvn test -Dtest=com.streamchat.integration.AuthIntegrationTest
```

## Run locally

### Quick start (dev profile, H2 database)

1. Build:

```bash
mvn clean package
```

2. Run:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

The application will start on `http://localhost:8080` with an in-memory H2 database (dev only).

### Docker Compose (local PostgreSQL + Redis stack)

Prerequisites: Docker and Docker Compose installed.

1. Start the full stack:

```bash
docker-compose up -d
```

This will start:
- **stream-chat-app** on `http://localhost:8080`
- **PostgreSQL** on `localhost:5432` (database: `stream_chat_db`)
- **Redis** on `localhost:6379`

2. View logs:

```bash
docker-compose logs -f stream-chat-app
```

3. Stop the stack:

```bash
docker-compose down
```

To persist data across restarts, the created volumes (`postgres-storage`, `redis-storage`) are preserved.

## API Documentation

OpenAPI UI (Swagger UI) is typically available at:

- `http://localhost:8080/swagger-ui.html`

(Exact path depends on your Springdoc configuration.)

## Project Structure (high level)

- `src/main/java` — application source code
- `src/main/resources` — Spring configuration and Flyway migrations
- `pom.xml` — Maven dependencies and build configuration
- `Dockerfile` — multi-stage Docker build for production containerization
- `docker-compose.yml` — local development stack (app + PostgreSQL + Redis)
- `ROADMAP.md` — staged plan for future improvements

