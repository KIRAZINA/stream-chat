# Nginx Reverse Proxy (Phase 3.1)

External traffic enters through the `nginx` service defined in `docker-compose.yml`
and is proxied to `stream-chat-app:8080` over the `stream-chat-network`.

## Layout

- `deploy/nginx/nginx.conf` — main context: worker processes, rate-limit zones,
  connection-limit zone, WebSocket upgrade map, upstream pool.
- `deploy/nginx/conf.d/stream-chat.conf` — server block: TLS termination,
  security headers, per-location rate/connection limits, proxy headers.
- `deploy/nginx/ssl/` — TLS certificates (not committed). Place
  `fullchain.pem` and `privkey.pem` here, or mount your own.

## Rate limiting

| Zone | Rate | Burst | Applied to |
|------|------|-------|------------|
| `api_limit` | 10 r/s | 20 nodelay | `/api/*` |
| `auth_limit` | 3 r/s | 5 nodelay | `/api/auth/login`, `/api/auth/register` |
| `ws_conn_limit` | 50 concurrent/IP | — | `/ws-chat` |

When a limit is exceeded Nginx responds `429 Too Many Requests` before the
request reaches Spring Boot. The application's `RateLimitService` remains as a
secondary per-user/per-stream enforcement layer.

## Testing rate limiting

```bash
# Burst REST requests
for i in $(seq 1 30); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/streams; done

# Burst auth requests
for i in $(seq 1 10); do curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/auth/login; done
```

Expect `429` responses once the configured burst is exceeded.

## TLS certificates

Generate a self-signed cert for local testing (do not commit private keys):

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout deploy/nginx/ssl/privkey.pem \
  -out deploy/nginx/ssl/fullchain.pem -subj "/CN=localhost"
```
