# Stream Chat Roadmap

This roadmap turns the current backend into a full stream chat product in 3 practical stages:

1. MVP
2. Beta
3. Production-ready

It is based on the current state of the project:

- solid Spring Boot backend foundation
- JWT auth, moderation, Redis integration, Flyway, tests
- basic WebSocket chat flow
- limited client experience
- several modeled features that are not enforced yet

## Phase 1: MVP

Goal: make the chat feel complete and reliable for a small real product demo.

### Focus

- stabilize realtime delivery
- finish the missing core chat rules
- expose enough APIs for a real frontend
- improve developer setup and docs

### Tasks

- Rework message fan-out so each message is delivered exactly once.
  - Choose one delivery path per deployment mode.
  - Avoid local `@SendTo` plus Redis re-broadcast duplication.
  - Add tests for single-instance and Redis-backed delivery.
- Enforce stored chat settings in message processing.
  - `slowModeEnabled`
  - `followersOnlyMode`
  - `subscribersOnlyMode`
  - `emoteOnlyMode`
  - per-role rate limit overrides
- Add recent chat history API.
  - `GET /api/streams/{streamKey}/messages?before=<cursor>&limit=<n>`
  - return only non-deleted messages
  - support reconnect and initial page load
- Improve moderation actions.
  - broadcast message deletion events
  - support bulk delete by user
  - return structured moderation event payloads
- Replace raw request maps with DTOs and validation.
  - stream create/update
  - settings update
  - moderation requests where relevant
- Clean up environment configuration.
  - do not hardcode `dev` as the default production startup profile
  - align WebSocket origin handling with configured CORS policy
  - document required env vars clearly
- Fix documentation and project hygiene.
  - add this roadmap to the README
  - remove stale mentions of missing Docker files or add them later
  - fix text encoding issues in static pages and README

### Frontend Deliverables

- Replace the current test page with a real chat client page or separate frontend app.
- Support:
  - login
  - stream join
  - chat history preload
  - reconnect with token reuse
  - message deletion updates
  - basic moderator actions

### Exit Criteria

- a streamer can create a stream, go live, and use chat with moderators
- no duplicate messages with Redis enabled
- reconnect restores recent history
- chat settings visibly affect behavior
- the project can be demoed end-to-end without manual Swagger-only steps

## Phase 2: Beta

Goal: make the product pleasant to use for real users and resilient under moderate traffic.

### Focus

- richer chat UX
- stronger moderation
- better observability
- more realistic load behavior

### Tasks

- Introduce richer message model.
  - message ids suitable for client reconciliation
  - reply metadata
  - system events separated from normal chat messages
  - deleted message tombstones if needed by UI
- Finish badge and emote support properly.
  - compute badges in the backend response
  - stop rendering HTML in the backend
  - send emote tokens or structured fragments instead
  - render emotes safely on the client
- Add moderation improvements.
  - AutoMod pipeline for suspicious content
  - shadow ban / silent moderation tools
  - audit-friendly moderation history
  - clearer permission model for broadcaster, mod, VIP, subscriber
- Add stream presence features.
  - active chatter count
  - lightweight viewer presence strategy
  - join/leave noise reduction
- Add observability and operations basics.
  - metrics for send rate, rejected messages, moderation actions
  - structured logs with stream key and user id
  - health checks for DB and Redis
  - alertable failure signals
- Improve persistence and query performance.
  - pagination-friendly message queries
  - indexes for stream + created_at + deleted state
  - retention strategy for chat history
- Add better tests.
  - websocket integration tests
  - Redis integration tests
  - load-oriented tests for rate limits and moderation paths

### Frontend Deliverables

- polished chat panel
- badges, colors, emotes, moderation UI
- message states for deleted / failed / rate-limited events
- virtualized message list for large chats
- clearer error and reconnect UX

### Exit Criteria

- the product is usable by external testers
- moderation works without admin-only shortcuts
- message rendering is safe and structured
- system behavior is observable under moderate load
- frontend no longer feels like a debug tool

## Phase 3: Production-ready

Goal: prepare the platform for larger rooms, safer operations, and long-term evolution.

### Focus

- scalability
- reliability
- security hardening
- product operations

### Tasks

- Revisit transport and scaling architecture.
  - evaluate whether STOMP + SockJS remains sufficient
  - consider a dedicated gateway or event-driven chat pipeline for high-volume rooms
  - define backpressure and fan-out strategy
- Introduce delivery guarantees and replay strategy.
  - idempotent message publishing
  - resilient reconnect flow
  - optional short replay window from Redis or dedicated store
- Harden security.
  - stricter origin policy
  - token rotation / refresh strategy for long sessions
  - abuse prevention by IP and account reputation
  - audit logs for admin actions
- Prepare real deployment workflow.
  - add Dockerfile and docker-compose for local stack
  - CI pipeline for tests and packaging
  - environment-specific configs and secrets management
  - rollout and rollback instructions
- Add data lifecycle rules.
  - retention policies
  - deletion workflows
  - archival decisions for old chat history
- Add product-level capabilities if the goal is a true Twitch-like experience.
  - pinned announcements
  - channel points or reward-triggered messages
  - clip or event notifications
  - user reputation and trust levels
  - creator-defined chat automation

### Exit Criteria

- safe production deployment path exists
- chat remains stable across multiple instances
- scaling bottlenecks are measured and documented
- operations team can detect and triage failures quickly
- security defaults are acceptable for public internet exposure

## Recommended Execution Order

If only the next few weeks matter, ship work in this order:

1. Fix duplicate delivery risk and finalize settings enforcement.
2. Add recent message history and message deletion events.
3. Replace request maps with validated DTOs.
4. Build a real frontend chat experience.
5. Finish badges, emotes, and moderator UX.
6. Add metrics, Redis integration coverage, and load checks.

## What To Build Next In This Repository

If work starts immediately, the highest-value first implementation batch is:

1. message delivery cleanup
2. chat history REST endpoint
3. settings enforcement in `ChatService`
4. DTO validation for settings and stream APIs
5. frontend chat page replacement
