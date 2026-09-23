package com.fortress.chat.dto.response;

import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserResponse {
    private String uid;
    private String email;
    private String name;
    private String dpUrl;
    private Boolean isOnline;
    private Instant lastSeen;
    private Instant createdAt;
}
