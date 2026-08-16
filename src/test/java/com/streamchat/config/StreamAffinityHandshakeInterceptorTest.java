package com.streamchat.config;

import com.streamchat.repository.StreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Track A · Item 1: the stream-affinity handshake interceptor must read the
 * {streamKey} path variable, reject unknown streams, and stash the key in the
 * session attributes that become the WebSocket session attributes.
 */
class StreamAffinityHandshakeInterceptorTest {

    private StreamRepository streamRepository;
    private StreamAffinityHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        streamRepository = mock(StreamRepository.class);
        interceptor = new StreamAffinityHandshakeInterceptor(streamRepository);
    }

    @Test
    void beforeHandshake_validStream_storesStreamKeyInAttributes() {
        when(streamRepository.existsByStreamKey("stream-abc")).thenReturn(true);
        ServerHttpRequest request = requestWithTemplateVariable("stream-abc");

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request, null, mock(WebSocketHandler.class), attributes);

        assertTrue(accepted, "handshake for an existing stream must be accepted");
        assertEquals("stream-abc", attributes.get(StreamAffinityHandshakeInterceptor.STREAM_KEY_ATTRIBUTE));
    }

    @Test
    void beforeHandshake_missingStream_rejectsHandshake() {
        when(streamRepository.existsByStreamKey("stream-missing")).thenReturn(false);
        ServerHttpRequest request = requestWithTemplateVariable("stream-missing");

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request, null, mock(WebSocketHandler.class), attributes);

        assertFalse(accepted, "handshake for a nonexistent stream must be rejected");
        assertFalse(attributes.containsKey(StreamAffinityHandshakeInterceptor.STREAM_KEY_ATTRIBUTE));
    }

    @Test
    void beforeHandshake_missingPathVariable_rejectsHandshake() {
        ServerHttpRequest request = new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws-chat/stream/"));

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request, null, mock(WebSocketHandler.class), attributes);

        assertFalse(accepted, "handshake without a streamKey must be rejected");
    }

    @Test
    void beforeHandshake_attributeFallback_parsesPathSegment() {
        when(streamRepository.existsByStreamKey("stream-from-path")).thenReturn(true);
        // No URI_TEMPLATE_VARIABLES attribute; the interceptor parses the path.
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/ws-chat/stream/stream-from-path");
        ServerHttpRequest request = new ServletServerHttpRequest(raw);

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request, null, mock(WebSocketHandler.class), attributes);

        assertTrue(accepted);
        assertEquals("stream-from-path", attributes.get(StreamAffinityHandshakeInterceptor.STREAM_KEY_ATTRIBUTE));
    }

    private ServerHttpRequest requestWithTemplateVariable(String streamKey) {
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/ws-chat/stream/" + streamKey);
        Map<String, String> vars = new HashMap<>();
        vars.put("streamKey", streamKey);
        raw.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, vars);
        return new ServletServerHttpRequest(raw);
    }
}