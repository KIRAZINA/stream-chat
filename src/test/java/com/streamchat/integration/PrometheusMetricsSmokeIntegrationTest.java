package com.streamchat.integration;

import com.streamchat.service.MetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track A · Item 5 (A5.4): smoke — after a broadcast is triggered, GET
 * /actuator/prometheus exposes the local-first counters under their rendered
 * Prometheus names.
 *
 * <p>Micrometer {@code chat.broadcast.local}/{@code chat.broadcast.orphaned}
 * render as {@code chat_broadcast_local_total}/{@code chat_broadcast_orphaned_total}
 * (dots → underscores, {@code _total} suffix) — the same mapping asserted at the
 * meter level in {@code MessageBroadcastServiceMetricsTest} (A5.1).
 *
 * <p>Endpoint + exporter are enabled here the way prod enables them
 * (application-prod.properties), with the Boot 3.2.1 property name
 * {@code management.prometheus.metrics.export.enabled}; the exposure is pinned
 * to {@code prometheus} so the test fails for the right reason (missing metric)
 * rather than a 404 on an unexposed endpoint.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=prometheus",
            "management.prometheus.metrics.export.enabled=true",
        })
@ActiveProfiles("dev")
class PrometheusMetricsSmokeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MetricsService metricsService;

    @Test
    void prometheusScrape_exposesBroadcastCounters() throws Exception {
        metricsService.recordBroadcastLocal();
        metricsService.recordBroadcastOrphaned();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/prometheus"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "the prometheus endpoint must be exposed and enabled");
        assertTrue(response.body().contains("chat_broadcast_local_total"),
                "scrape must expose chat_broadcast_local_total after a local broadcast");
        assertTrue(response.body().contains("chat_broadcast_orphaned_total"),
                "scrape must expose chat_broadcast_orphaned_total after an orphaned broadcast");
    }
}