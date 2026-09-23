package com.fortress.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * User entity — primary key is the Firebase Auth UID (not auto-generated).
 * This mirrors the existing Prisma User model where uid is the Firebase UID.
 */
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "uid", length = 128)
    private String uid;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "dp_url")
    private String dpUrl;

    @Column(name = "is_online", nullable = false)
    @Builder.Default
    private Boolean isOnline = false;

    @Column(name = "last_seen", nullable = false)
    @Builder.Default
    private Instant lastSeen = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;
}
