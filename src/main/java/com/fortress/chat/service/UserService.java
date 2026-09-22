package com.fortress.chat.service;

import com.fortress.chat.dto.response.UserResponse;
import com.fortress.chat.entity.User;
import com.fortress.chat.exception.ResourceNotFoundException;
import com.fortress.chat.repository.ConnectionRepository;
import com.fortress.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final ConnectionRepository connectionRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final org.springframework.beans.factory.ObjectProvider<com.fortress.chat.websocket.RoomManager> roomManagerProvider;

    /**
     * Sync/upsert user on login — mirrors existing POST /api/users/sync.
     * Creates the user if they don't exist, updates lastSeen if they do.
     * If user logs in with the same email but a new UID, migrates old references.
     */
    @Transactional
    public UserResponse syncUser(String uid, String email, String name, String dpUrl) {
        User user = userRepository.findByUid(uid).orElse(null);

        if (user != null) {
            // Update existing
            if (email != null) user.setEmail(email);
            if (name != null) user.setName(name);
            if (dpUrl != null) user.setDpUrl(dpUrl);
            user.setIsDeleted(false);
            user.setLastSeen(Instant.now());
            user = userRepository.save(user);
        } else {
            String resolvedEmail = (email != null && !email.isBlank()) ? email :
                    uid + "@fortress.chat";
            String resolvedName = (name != null && !name.isBlank()) ? name :
                    (email != null ? email.split("@")[0] : "User");

            // Check if user already exists with this email under an old UID
            User existingByEmail = userRepository.findByEmail(resolvedEmail).orElse(null);
            if (existingByEmail != null && !existingByEmail.getUid().equals(uid)) {
                String oldUid = existingByEmail.getUid();
                log.info("Migrating user references from old UID {} to new UID {} for email {}",
                        oldUid, uid, resolvedEmail);

                // 1. Temporarily create new user with temp email so FKs can reference it
                user = User.builder()
                        .uid(uid)
                        .email("temp_" + System.currentTimeMillis() + "@temp.local")
                        .name(resolvedName)
                        .dpUrl(dpUrl != null ? dpUrl : existingByEmail.getDpUrl())
                        .isOnline(false)
                        .isDeleted(false)
                        .lastSeen(Instant.now())
                        .createdAt(existingByEmail.getCreatedAt())
                        .build();
                userRepository.saveAndFlush(user);

                // 2. Migrate foreign keys
                entityManager.createNativeQuery("UPDATE connections SET from_uid = :newUid WHERE from_uid = :oldUid")
                        .setParameter("newUid", uid).setParameter("oldUid", oldUid).executeUpdate();
                entityManager.createNativeQuery("UPDATE connections SET to_uid = :newUid WHERE to_uid = :oldUid")
                        .setParameter("newUid", uid).setParameter("oldUid", oldUid).executeUpdate();
                entityManager.createNativeQuery("UPDATE chat_room_participants SET uid = :newUid WHERE uid = :oldUid")
                        .setParameter("newUid", uid).setParameter("oldUid", oldUid).executeUpdate();
                entityManager.createNativeQuery("UPDATE messages SET sender_id = :newUid WHERE sender_id = :oldUid")
                        .setParameter("newUid", uid).setParameter("oldUid", oldUid).executeUpdate();
                entityManager.createNativeQuery("UPDATE message_receipts SET uid = :newUid WHERE uid = :oldUid")
                        .setParameter("newUid", uid).setParameter("oldUid", oldUid).executeUpdate();

                // 3. Delete old user
                userRepository.delete(existingByEmail);
                userRepository.flush();

                // 4. Update new user to the actual email
                user.setEmail(resolvedEmail);
                user = userRepository.save(user);
            } else {
                user = User.builder()
                        .uid(uid)
                        .email(resolvedEmail)
                        .name(resolvedName)
                        .dpUrl(dpUrl)
                        .isOnline(false)
                        .isDeleted(false)
                        .lastSeen(Instant.now())
                        .createdAt(Instant.now())
                        .build();
                user = userRepository.save(user);
            }
        }

        return toResponse(user);
    }

    /**
     * Search users by email (case-insensitive, partial match).
     * Excludes the requesting user and deleted accounts.
     */
    public List<UserResponse> searchByEmail(String email, String excludeUid) {
        return userRepository.findByEmailContainingIgnoreCaseAndUidNotAndIsDeletedFalse(email.trim(), excludeUid)
                .stream()
                .limit(10)
                .map(UserService::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get user by UID.
     */
    public UserResponse getUserByUid(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toResponse(user);
    }

    /**
     * Update user online/offline presence.
     */
    @Transactional
    public void setPresence(String uid, boolean isOnline) {
        userRepository.findByUid(uid).ifPresent(user -> {
            user.setIsOnline(isOnline);
            user.setLastSeen(Instant.now());
            userRepository.save(user);
        });
    }

    /**
     * Delete account — anonymizes the user into a "Deleted Account" tombstone.
     * Retains chat rooms and message history for other contacts.
     * Releases original email and deletes user from Firebase Auth.
     */
    @Transactional
    public void deleteAccount(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uid));

        log.info("Tombstoning account for UID: {}", uid);

        // 1. Anonymize user record — keep messages and chat history intact for contacts
        user.setIsDeleted(true);
        user.setName("Deleted Account");
        // Release original email so user can re-register in future if desired
        user.setEmail("deleted_" + uid + "@deleted.fortress");
        user.setDpUrl(null);
        user.setIsOnline(false);
        user.setLastSeen(Instant.now());
        userRepository.save(user);

        // 2. Remove all connection rows involving this user
        connectionRepository.deleteAllForUser(uid);

        // 3. Delete from Firebase Auth
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().deleteUser(uid);
            log.info("Successfully deleted user {} from Firebase Auth", uid);
        } catch (Exception e) {
            log.warn("Could not delete user {} from Firebase Auth (dev mode or already deleted): {}", uid, e.getMessage());
        }

        // 4. Update presence
        setPresence(uid, false);
    }

    public static UserResponse toResponse(User user) {
        if (user == null) return null;
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            String shortId = user.getUid() != null && user.getUid().length() > 8
                    ? user.getUid().substring(0, 8) : user.getUid();
            return UserResponse.builder()
                    .uid("deleted_" + shortId)
                    .email("deleted@fortress.local")
                    .name("Deleted Account")
                    .dpUrl(null)
                    .isOnline(false)
                    .lastSeen(user.getLastSeen())
                    .createdAt(user.getCreatedAt())
                    .build();
        }
        return UserResponse.builder()
                .uid(user.getUid())
                .email(user.getEmail())
                .name(user.getName())
                .dpUrl(user.getDpUrl())
                .isOnline(user.getIsOnline())
                .lastSeen(user.getLastSeen())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
