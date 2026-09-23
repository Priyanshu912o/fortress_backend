package com.fortress.chat.entity;

import com.fortress.chat.entity.enums.ConnectionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;

/**
 * Connection entity — friend request system.
 * Unique constraint on (from_uid, to_uid) prevents duplicate requests.
 */
@Entity
@Table(name = "connections",
       uniqueConstraints = @UniqueConstraint(columnNames = {"from_uid", "to_uid"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Connection {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "from_uid", nullable = false, length = 128)
    private String fromUid;

    @Column(name = "to_uid", nullable = false, length = 128)
    private String toUid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConnectionStatus status = ConnectionStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    // --- Relationships (for eager loading in queries) ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_uid", referencedColumnName = "uid", insertable = false, updatable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_uid", referencedColumnName = "uid", insertable = false, updatable = false)
    private User toUser;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
