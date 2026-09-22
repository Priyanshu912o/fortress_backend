package com.fortress.chat.repository;

import com.fortress.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    Optional<Message> findByClientMessageId(String clientMessageId);

    /** Cursor-based pagination: fetch messages before the cursor timestamp */
    List<Message> findByChatRoomIdAndDeletedAtIsNullAndSentAtBeforeOrderBySentAtDesc(
            String chatRoomId, Instant cursor, Pageable pageable);

    /** First page (no cursor) */
    List<Message> findByChatRoomIdAndDeletedAtIsNullOrderBySentAtDesc(
            String chatRoomId, Pageable pageable);

    /** Find unread messages from other senders (for mark-read) */
    List<Message> findByChatRoomIdAndSenderIdNot(String chatRoomId, String senderUid);
}
