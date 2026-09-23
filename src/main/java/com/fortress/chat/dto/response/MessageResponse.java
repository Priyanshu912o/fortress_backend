package com.fortress.chat.dto.response;

import lombok.*;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MessageResponse {
    private String id;
    private String clientMessageId;
    private String chatRoomId;
    private String senderId;
    private String body;
    private Instant sentAt;
    private Instant deletedAt;
    private UserResponse sender;
    private List<ReceiptResponse> receipts;

    @Data
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class ReceiptResponse {
        private String id;
        private String messageId;
        private String uid;
        private Instant deliveredAt;
        private Instant readAt;
    }
}
