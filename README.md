# Stream Chat Platform

Real-time chat system for live streaming. Built with Spring Boot backend and React frontend. Similar to Twitch/YouTube Live chat with moderation, WebSocket support, and JWT auth.

## Features

- 💬 Real-time messaging via WebSocket (STOMP)
- 🔐 JWT authentication
- 🛡️ Moderation tools (timeout, ban, delete messages)
- ⚡ Rate limiting per user role
- 🎭 User roles (Broadcaster, Moderator, VIP, Subscriber)
- 📊 Chat modes (slow mode, followers-only, subscribers-only)
- 🚀 Scalable with Redis pub/sub
- 📝 Message history & replay for reconnects
- 🔍 Audit logging for all moderation actions

## Quick Start

### Backend (Spring Boot)

```bash
mvn spring-boot:run
```

Runs on `http://localhost:8080` with H2 in-memory database for development.

### Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173` and connects to backend at `http://localhost:8080/api`.

## API Docs

Swagger UI available at `http://localhost:8080/swagger-ui.html`

### Main Endpoints

- **POST** `/api/auth/register` - Register new user
- **POST** `/api/auth/login` - Login and get JWT
- **GET** `/api/users/me` - Get current user profile
- **GET/POST** `/api/streams` - Stream management
- **GET** `/api/streams/{id}/messages` - Chat history
- **POST/GET** `/api/streams/{id}/moderate/*` - Moderation

## Tech Stack

**Backend:**
- Java 17, Spring Boot 3.2.1
- PostgreSQL, Redis, H2
- JJWT authentication
- Flyway migrations

**Frontend:**
- React 18, TypeScript
- Vite, Tailwind CSS
- @stomp/stompjs for WebSocket
- Zustand for state management

## Setup for Production

1. Copy `.env.example` to `.env` and set your values
2. Use Docker: `docker-compose up -d`
3. Or set Spring profile to `prod` and provide environment variables

## Testing

Backend tests with Maven:
```bash
mvn test
```

Frontend tests:
```bash
cd frontend && npm test
```

## License

MIT
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

## Frontend

The React frontend is located in `frontend/`.

Development:

```bash
cd frontend
npm install
npm run dev
```

Production build:

```bash
cd frontend
npm install
npm run build
```

The frontend can also run in Docker through `docker-compose` on port `3000`.

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
