package com.fortress.chat.kafka;

import lombok.*;
import java.time.Instant;

/**
 * Event published to Kafka when a message is successfully persisted.
 * The consumer processes this to simulate delivery (mark delivered in DB).
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MessageSentEvent {
    private String messageId;
    private String chatroomId;
    private String senderId;
    private String content;
    private Instant sentAt;
}
