package com.fortress.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;

/**
 * Message entity — text messages within a chat room.
 * clientMessageId allows idempotent upserts to prevent duplicate messages
 * when the client retries a send.
 *
 * Index on (chat_room_id, sent_at DESC) supports cursor-based pagination.
 */
@Entity
@Table(name = "messages",
       indexes = {
           @Index(name = "idx_messages_room_sent", columnList = "chat_room_id, sent_at DESC"),
           @Index(name = "idx_messages_client_id", columnList = "client_message_id")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Message {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "client_message_id", nullable = false, unique = true)
    private String clientMessageId;

    @Column(name = "chat_room_id", nullable = false, length = 36)
    private String chatRoomId;

    @Column(name = "sender_id", nullable = false, length = 128)
    private String senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at", nullable = false)
    @Builder.Default
    private Instant sentAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // --- Relationships ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", referencedColumnName = "id", insertable = false, updatable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", referencedColumnName = "uid", insertable = false, updatable = false)
    private User sender;
}
