# Stream Chat Platform

A Spring Boot (Java 17) backend for a real-time chat system designed for live streaming.

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
- Docker + Docker Compose

## Prerequisites

- Java 17+
- Maven 3.9+ (or Maven Wrapper if you add it)
- Docker (optional, for running with Compose)

## Configuration

The app uses Spring profiles:

- `dev` (default): H2 in-memory database (see `application-dev.properties`)
- `docker`: PostgreSQL + Redis for running via Docker Compose (see `application-docker.properties`)
- `prod`: configuration via environment variables (see `application-prod.properties`)

### Environment variables (production / docker)

- `JWT_SECRET` (required in `docker` / `prod`) — **use a long secret (64+ chars)**
- `JWT_EXPIRATION` (optional, default `86400000`)

For `prod` profile:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT` (optional, default `6379`)
- `REDIS_PASSWORD` (optional)

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

## Known limitations / TODO

- If WebSocket messages are sent without a valid authenticated principal, handlers may fail (this should be guarded at the inbound interceptor level).

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

## Run locally (dev profile)

1. Build:

```bash
mvn clean package
```

2. Run:

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

## Run with Docker Compose

1. Start services:

```bash
docker compose up --build
```

2. The application will be available at `http://localhost:8080`.

Note: `docker-compose.yml` contains development defaults (database credentials and a placeholder `JWT_SECRET`). Change them before any real deployment.

## API Documentation

OpenAPI UI (Swagger UI) is typically available at:

- `http://localhost:8080/swagger-ui.html`

(Exact path depends on your Springdoc configuration.)

## Project Structure (high level)

- `src/main/java` — application source code
- `src/main/resources` — Spring configuration and Flyway migrations
- `docker-compose.yml` / `Dockerfile` — containerization

