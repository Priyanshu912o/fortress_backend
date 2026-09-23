package com.fortress.chat.dto.response;

import com.fortress.chat.entity.enums.ConnectionStatus;
import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConnectionResponse {
    private String id;
    private String fromUid;
    private String toUid;
    private ConnectionStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private UserResponse fromUser;
    private UserResponse toUser;
    private String chatRoomId;
}
