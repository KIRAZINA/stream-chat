package com.streamchat.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Paginated chat history response for REST clients.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryResponse {

    private List<ChatMessageDTO> messages;
    private Long nextCursor;
    private boolean hasMore;
}
