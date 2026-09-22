package com.fortress.chat.repository;

import com.fortress.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    /** Find all chat rooms the user participates in, ordered by last activity */
    @Query("SELECT cr FROM ChatRoom cr JOIN ChatRoomParticipant p ON cr.id = p.chatRoomId " +
           "WHERE p.uid = :uid ORDER BY cr.lastMessageAt DESC NULLS LAST, cr.createdAt DESC")
    List<ChatRoom> findAllByParticipantUid(@Param("uid") String uid);

    /** Find existing direct chat room between two users */
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.isDirect = true AND " +
           "EXISTS (SELECT 1 FROM ChatRoomParticipant p1 WHERE p1.chatRoomId = cr.id AND p1.uid = :uid1) AND " +
           "EXISTS (SELECT 1 FROM ChatRoomParticipant p2 WHERE p2.chatRoomId = cr.id AND p2.uid = :uid2)")
    Optional<ChatRoom> findDirectBetween(@Param("uid1") String uid1, @Param("uid2") String uid2);
}
