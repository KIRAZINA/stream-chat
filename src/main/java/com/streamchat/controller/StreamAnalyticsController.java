package com.streamchat.controller;

import com.streamchat.model.dto.analytics.StreamAnalyticsSummaryDTO;
import com.streamchat.model.dto.analytics.StreamTimeBucketMetricsDTO;
import com.streamchat.repository.StreamRepository;
import com.streamchat.service.StreamAnalyticsService;
import com.streamchat.service.StreamAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
public class StreamAnalyticsController {

    private final StreamAnalyticsService streamAnalyticsService;
    private final StreamRepository streamRepository;
    private final StreamAuthorizationService streamAuthorizationService;

    @GetMapping("/{streamKey}/analytics/summary")
    public ResponseEntity<StreamAnalyticsSummaryDTO> getSummary(
            @PathVariable String streamKey,
            @AuthenticationPrincipal UserDetails principal) {
        if (principal == null || !streamAuthorizationService.canModerate(streamKey, principal.getUsername())) {
            return ResponseEntity.status(403).build();
        }
        Long streamId = streamRepository.findByStreamKey(streamKey).map(s -> s.getId()).orElse(null);
        return ResponseEntity.ok(streamAnalyticsService.getSummary(streamKey, streamId));
    }

    @GetMapping("/{streamKey}/analytics/time-series")
    public ResponseEntity<List<StreamTimeBucketMetricsDTO>> getTimeSeries(
            @PathVariable String streamKey,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @AuthenticationPrincipal UserDetails principal) {
        if (principal == null || !streamAuthorizationService.canModerate(streamKey, principal.getUsername())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(streamAnalyticsService.getTimeSeries(streamKey, interval, from, to));
    }
}
