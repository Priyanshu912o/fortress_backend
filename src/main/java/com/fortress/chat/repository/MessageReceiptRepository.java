package com.fortress.chat.repository;

import com.fortress.chat.entity.MessageReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, String> {

    Optional<MessageReceipt> findByMessageIdAndUid(String messageId, String uid);

    List<MessageReceipt> findByMessageId(String messageId);
}
