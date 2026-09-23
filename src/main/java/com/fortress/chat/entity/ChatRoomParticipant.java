package com.fortress.chat.entity;

import com.fortress.chat.entity.enums.ParticipantRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;

/**
 * ChatRoomParticipant — join table between ChatRoom and User.
 * Tracks role (ADMIN/MEMBER) and per-user unread count.
 */
@Entity
@Table(name = "chat_room_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"chat_room_id", "uid"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatRoomParticipant {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "chat_room_id", nullable = false, length = 36)
    private String chatRoomId;

    @Column(name = "uid", nullable = false, length = 128)
    private String uid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "unread_count", nullable = false)
    @Builder.Default
    private Integer unreadCount = 0;

    @Column(name = "joined_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();

    // --- Relationships ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", referencedColumnName = "id", insertable = false, updatable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uid", referencedColumnName = "uid", insertable = false, updatable = false)
    private User user;
}
