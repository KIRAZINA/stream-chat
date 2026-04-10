package com.streamchat.model.dto;

import com.streamchat.model.enums.MessageFragmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Structured message fragment used by the frontend.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageFragmentDTO {

    private MessageFragmentType type;
    private String text;
    private String emoteCode;
    private String imageUrl;
}
