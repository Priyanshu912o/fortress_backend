package com.fortress.chat.repository;

import com.fortress.chat.entity.ChatRoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, String> {

    Optional<ChatRoomParticipant> findByChatRoomIdAndUid(String chatRoomId, String uid);

    List<ChatRoomParticipant> findByChatRoomId(String chatRoomId);

    List<ChatRoomParticipant> findByUid(String uid);

    boolean existsByChatRoomIdAndUid(String chatRoomId, String uid);

    /** Increment unread count for all participants except the sender */
    @Modifying
    @Query("UPDATE ChatRoomParticipant p SET p.unreadCount = p.unreadCount + 1 " +
           "WHERE p.chatRoomId = :chatRoomId AND p.uid <> :excludeUid")
    void incrementUnreadForOthers(@Param("chatRoomId") String chatRoomId, @Param("excludeUid") String excludeUid);

    /** Reset unread count for a specific user in a chat room */
    @Modifying
    @Query("UPDATE ChatRoomParticipant p SET p.unreadCount = 0 " +
           "WHERE p.chatRoomId = :chatRoomId AND p.uid = :uid")
    void resetUnreadCount(@Param("chatRoomId") String chatRoomId, @Param("uid") String uid);
}
