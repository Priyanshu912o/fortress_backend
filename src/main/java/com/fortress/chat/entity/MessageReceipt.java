package com.fortress.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;

/**
 * MessageReceipt — tracks per-user delivery and read status for each message.
 * Unique constraint on (message_id, uid) ensures one receipt per user per message.
 */
@Entity
@Table(name = "message_receipts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "uid"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MessageReceipt {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "message_id", nullable = false, length = 36)
    private String messageId;

    @Column(name = "uid", nullable = false, length = 128)
    private String uid;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    // --- Relationships ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uid", referencedColumnName = "uid", insertable = false, updatable = false)
    private User user;
}
