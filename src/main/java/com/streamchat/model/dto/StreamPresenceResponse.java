package com.streamchat.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response model for stream presence information.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamPresenceResponse {

    private int activeViewers;
}
