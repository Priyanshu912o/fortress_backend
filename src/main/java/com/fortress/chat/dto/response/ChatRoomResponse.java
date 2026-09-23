package com.fortress.chat.dto.response;

import lombok.*;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatRoomResponse {
    private String id;
    private Boolean isDirect;
    private String groupName;
    private String groupDpUrl;
    private String lastMessageBody;
    private Instant lastMessageAt;
    private String lastSenderId;
    private Instant createdAt;
    private Integer unreadCount;
    private List<ParticipantResponse> participants;

    @Data
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class ParticipantResponse {
        private String id;
        private String chatRoomId;
        private String uid;
        private String role;
        private Integer unreadCount;
        private Instant joinedAt;
        private UserResponse user;
    }
}
