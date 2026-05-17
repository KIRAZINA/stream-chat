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
    private Long ownerId;
    private String ownerUsername;
    private String title;
    private String description;
    private String status;
    private String category;
    private Integer viewerCount;
    private LocalDateTime startedAt;
    private LocalDateTime createdAt;
}