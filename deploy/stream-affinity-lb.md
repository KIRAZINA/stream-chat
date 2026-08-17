# A4 — Consistent-hash load balancer config (infra/docs, no Java)

Affinity requires all WebSocket connections for a `streamKey` to land on ONE
backend node, so `chat.broadcast.local-first=true` is safe. The frontend (A3)
already connects to the native `/ws-chat/stream/{streamKey}` endpoint, so the
LB can consistent-hash on the key in the path.

**N=1 (current `docker-compose.yml`):** no LB is needed and no config changes.
A single instance trivially satisfies affinity. Everything below only applies
when you scale the app to **N>1**.

**Do not enable the frontend flag / local-first until this LB config is live:**
without hashing, keyed connections round-robin across instances and
`local-first` would silently drop cross-instance deliveries.

---

## 1. Nginx reference config (copy-pasteable)

```nginx
# ---------------------------------------------------------------------------
# stream-chat LB — consistent-hash affinity for /ws-chat/stream/{streamKey}
# ---------------------------------------------------------------------------

# Keyed upstream: consistent hash (ketama) on the streamKey from the path.
# All WS for a given streamKey pin to one node.
upstream stream_chat_keyed {
    hash $streamKey consistent;      # ketama: only ~1/N keys remap on scale
    server chat-1:8080;
    server chat-2:8080;
    server chat-3:8080;
    keepalive 32;
}

# Legacy SockJS endpoint (/ws-chat) — plain round-robin rollback path.
upstream stream_chat_sockjs {
    server chat-1:8080;
    server chat-2:8080;
    server chat-3:8080;
    keepalive 32;
}

# Extract streamKey from the native WS path.
map $request_uri $streamKey {
    default                 "";
    ~^/ws-chat/stream/([^/?]+)   $1;
}

server {
    listen 80;
    server_name _;

    # Native stream-keyed WebSocket endpoint (Track A / A3).
    # Longest-prefix match wins over /ws-chat below.
    location /ws-chat/stream/ {
        proxy_pass http://stream_chat_keyed;   # no URI → original path preserved
        proxy_http_version 1.1;

        # WebSocket upgrade handshake.
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # MUST exceed app.websocket.heartbeat-interval-ms (10s) so heartbeat
        # frames keep the connection from being idled out at the LB.
        proxy_read_timeout 75s;
        proxy_send_timeout 75s;
        proxy_connect_timeout 5s;
    }

    # Legacy SockJS endpoint — round-robin across nodes (rollback path).
    location /ws-chat {
        proxy_pass http://stream_chat_sockjs;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 75s;
        proxy_send_timeout 75s;
    }

    # REST API + static frontend unchanged — keep your existing /api upstream
    # and static asset serving here (omitted: not part of the affinity change).
}
```

Notes:
- **Heartbeat-vs-idle-timeout:** the broker emits a heartbeat every 10s
  (`app.websocket.heartbeat-interval-ms=10000`). `proxy_read_timeout`/`
  proxy_send_timeout` (75s here) must be larger than that interval, otherwise
  the LB closes an otherwise-healthy socket between heartbeats. If you tune the
  broker interval, bump the timeouts with it.
- **JWT auth is unaffected:** the token travels in the STOMP CONNECT frame, not
  an HTTP header, so the LB needs no header forwarding for auth.
- **`map` caveat:** keyed requests always carry a streamKey segment, so
  `$streamKey` is never empty on the keyed upstream.

---

## 2. Envoy alternative (brief)

Two clusters with different load-balance policies; the HTTP connection manager
must allow WebSocket upgrades.

> **RING_HASH without a route `hash_policy` has NO affinity.** The ring only
> hashes when the route supplies a hash key; with no `hash_policy` (or when all
> policies fail to produce a hash) Envoy picks a *random* backend per request.
> The `hash_policy` below is therefore **required**, not optional, and it must
> hash on the streamKey, not the full path.

```yaml
static_resources:
  listeners:
    - name: ws_listener
      address: { socket_address: { address: 0.0.0.0, port_value: 80 } }
      filter_chains:
        - filters:
            - name: envoy.filters.network.http_connection_manager
              typed_config:
                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                stat_prefix: ws
                upgrade_configs:
                  - upgrade_type: websocket
                route_config:
                  virtual_hosts:
                    - name: ws
                      domains: ["*"]
                      routes:
                        - match: { prefix: "/ws-chat/stream/" }
                          route:
                            cluster: stream_chat_keyed
                            # REQUIRED: hash on the streamKey extracted from :path.
                            # The regex_rewrite-on-header hashing is available since
                            # Envoy 1.19; the ring then pins per-streamKey.
                            hash_policy:
                              - header:
                                  header_name: ":path"
                                  regex_rewrite:
                                    pattern:
                                      google_re2: { max_program_size: 100 }
                                      regex: "^/ws-chat/stream/([^/?]+).*$"
                                    substitution: "\\1"
                        - match: { prefix: "/ws-chat" }
                          route: { cluster: stream_chat_sockjs }
                http_filters:
                  - name: envoy.filters.http.router

  clusters:
    - name: stream_chat_keyed
      connect_timeout: 5s
      lb_policy: RING_HASH
      ring_hash_lb_config:
        minimum_ring_size: 1024
        maximum_ring_size: 16384
      load_assignment:
        cluster_name: stream_chat_keyed
        endpoints:
          - lb_endpoints:
              - endpoint: { address: { socket_address: { address: chat-1, port_value: 8080 } } }
              - endpoint: { address: { socket_address: { address: chat-2, port_value: 8080 } } }
              - endpoint: { address: { socket_address: { address: chat-3, port_value: 8080 } } }

    - name: stream_chat_sockjs
      connect_timeout: 5s
      lb_policy: ROUND_ROBIN
      load_assignment:
        cluster_name: stream_chat_sockjs
        endpoints:
          - lb_endpoints:
              - endpoint: { address: { socket_address: { address: chat-1, port_value: 8080 } } }
              - endpoint: { address: { socket_address: { address: chat-2, port_value: 8080 } } }
              - endpoint: { address: { socket_address: { address: chat-3, port_value: 8080 } } }
```

Notes:
- **Why regex_rewrite on `:path`:** hashing the full `:path` still works
  (`header_name: ":path"` with no rewrite), but the regex-rewrite form hashes
  ONLY the captured `streamKey`, so the ring distribution is per-stream, not
  per-path. If you need pre-1.19 compatibility, drop the `regex_rewrite` block
  and hash the full `:path` — affinity still holds because the key is a path
  segment.
- There is no `path` hash-policy type in the current `RouteAction.HashPolicy`
  proto (only `header`/`cookie`/`connection_properties`/`query_parameter`/
  `filter_state`) — "hash on the path" is expressed via the `:path` header.
- The upgrade handshake is hashed once; the upgraded TCP stream stays pinned to
  the chosen upstream for its lifetime.

---

## 3. Docker-compose note (N=1 vs N>1)

- **N=1 (current `docker-compose.yml`):** no LB, no config change. A single
  `stream-chat-app` trivially satisfies affinity. `VITE_USE_STREAM_AFFINITY`
  can remain `false` (safe default).
- **N>1 (production):** to enable affinity end-to-end you must, in order:
  1. Deploy N app instances behind this LB config (they share the existing
     Postgres + Redis, which are already in compose).
  2. Put the Nginx/Envoy config above in front of them.
  3. Set `VITE_USE_STREAM_AFFINITY=true` and `chat.broadcast.local-first=true`.
Flipping the flag before the LB is live would round-robin keyed connections
   and drop cross-instance deliveries under `local-first` — do not do it.
- **Rollback:** flip both flags **off** (`VITE_USE_STREAM_AFFINITY=false`,
  `chat.broadcast.local-first=false`) and the app resumes Redis fan-out, which
  needs no affinity and works on any topology — the emergency escape hatch.
  Affinity can stay deployed behind the LB; the flags just stop using it.

---

## 4. Tuning note

- **Nginx ketama:** `hash $streamKey consistent;` uses a ketama-compatible
  ring with a fixed 160 points per server (not tunable in nginx). Scaling the
  pool remaps only ~1/N of the stream keys.
- **Envoy virtual nodes:** `ring_hash_lb_config.minimum_ring_size` /
  `maximum_ring_size` tune the ring granularity (default 1024). Larger rings
  spread traffic more evenly at the cost of more memory and slightly more
  remapping on membership change.
- **Mega-stream hotspot:** consistent hashing *guarantees co-location* but does
  not spread one huge stream across nodes — a single popular stream pins to ONE
  node and all its WS land there. Mitigations:
  - Scale the hot node vertically, or
  - **Future sub-sharding:** hash `streamKey + ":" + shardId` so a stream can be
    split across nodes; this needs client-side shard negotiation plus a
    per-shard topic convention (out of scope for Track A).
- **Node removal:** with equal-weight servers, removing one node remaps ~1/N of
  keys (Nginx ketama) or the fraction served by the removed node (Envoy
  RING_HASH). The 2.5 gap-replay backfill on the client covers the reconnect
  window regardless of which node the key re-homes to.