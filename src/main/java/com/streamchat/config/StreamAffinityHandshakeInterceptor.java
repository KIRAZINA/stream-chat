package com.streamchat.config;

import com.streamchat.repository.StreamRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Stream-affinity handshake interceptor for the native /ws-chat/stream/{streamKey}
 * endpoint (Track A · Item 1).
 *
 * <p>The streamKey path variable is exposed by the WebSocketHandlerMapping
 * (a SimpleUrlHandlerMapping subclass) as the servlet request attribute
 * {@link HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE}. This interceptor
 * reads it, verifies the stream still exists, and stores the key in the session
 * attributes so downstream handlers (broadcast, presence) can colocate by stream.
 *
 * <p>The lookup is intentionally a cheap exists-by-key check (cached/single-row)
 * because it runs on every connection handshake.
 */
@Slf4j
@RequiredArgsConstructor
public class StreamAffinityHandshakeInterceptor implements HandshakeInterceptor {

    public static final String STREAM_KEY_ATTRIBUTE = "streamKey";

    private final StreamRepository streamRepository;

    /**
     * Extract the {streamKey} path variable, reject the handshake when the
     * stream is unknown, otherwise record the key in the session attributes.
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String streamKey = extractStreamKey(request);
        if (streamKey == null || streamKey.isBlank()) {
            log.warn("Rejecting stream-affinity handshake: no streamKey in path {}",
                    request.getURI().getPath());
            return false;
        }

        if (!streamRepository.existsByStreamKey(streamKey)) {
            log.warn("Rejecting stream-affinity handshake: stream '{}' not found", streamKey);
            return false;
        }

        attributes.put(STREAM_KEY_ATTRIBUTE, streamKey);
        log.debug("Accepted stream-affinity handshake for stream '{}'", streamKey);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no post-handshake work needed
    }

    /**
     * Read the streamKey from the URI template variables the handler mapping
     * exposed on the underlying servlet request. Falls back to parsing the path
     * when the attribute is unavailable (defensive, non-Spring path).
     */
    private String extractStreamKey(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            Object vars = httpRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (vars instanceof Map<?, ?> map) {
                Object streamKey = map.get("streamKey");
                if (streamKey instanceof String key) {
                    return key;
                }
            }
        }

        String path = request.getURI().getPath();
        String prefix = "/ws-chat/stream/";
        if (path != null && path.startsWith(prefix)) {
            String key = path.substring(prefix.length());
            int slash = key.indexOf('/');
            if (slash >= 0) {
                key = key.substring(0, slash);
            }
            return key;
        }
        return null;
    }
}