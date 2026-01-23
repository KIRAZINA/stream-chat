package com.streamchat.model.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for streams.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamDTO {

    private Long id;
    private String streamKey;
    private Long userId;
    private String username;
    private String title;
    private String description;
    private Boolean isLive;
    private Integer viewerCount;
    private LocalDateTime startedAt;
    private LocalDateTime createdAt;
}