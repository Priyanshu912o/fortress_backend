package com.fortress.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;

/**
 * ChatRoom entity — supports both direct (1:1) and group chat rooms.
 * Denormalized fields (lastMessageBody, lastMessageAt, lastSenderId) enable
 * fast chat-list queries without joining the messages table.
 */
@Entity
@Table(name = "chat_rooms")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatRoom {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "is_direct", nullable = false)
    @Builder.Default
    private Boolean isDirect = true;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "group_dp_url")
    private String groupDpUrl;

    // --- Denormalized for fast chat-list queries ---

    @Column(name = "last_message_body")
    private String lastMessageBody;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_sender_id", length = 128)
    private String lastSenderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
