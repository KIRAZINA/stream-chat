package com.streamchat.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmoteDTO {
    private Long id;
    private String code;
    private String imageUrl;
    private boolean isGlobal;
    private Long streamId;
}
