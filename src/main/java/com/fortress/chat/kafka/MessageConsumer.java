package com.fortress.chat.kafka;

import com.fortress.chat.entity.MessageReceipt;
import com.fortress.chat.repository.ChatRoomParticipantRepository;
import com.fortress.chat.repository.MessageReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/**
 * Kafka consumer — reads from fortress.messages and simulates delivery.
 *
 * On receiving a MessageSentEvent, it creates delivery receipts for all
 * participants in the chat room (except the sender). This demonstrates
 * producer/consumer decoupling: message ingestion (via REST) is decoupled
 * from delivery processing (via Kafka consumer).
 *
 * In a real system, this consumer would also push notifications (FCM/APNS),
 * trigger WebSocket delivery events, etc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageConsumer {

    private final MessageReceiptRepository receiptRepository;
    private final ChatRoomParticipantRepository participantRepository;

    @KafkaListener(topics = "fortress.messages", groupId = "fortress-chat-consumer")
    @Transactional
    public void onMessageSent(MessageSentEvent event) {
        log.info("📨 Kafka consumer received: messageId={}, chatroomId={}, senderId={}",
                event.getMessageId(), event.getChatroomId(), event.getSenderId());

        Instant deliveredAt = Instant.now();

        // Create delivery receipts for all participants except sender
        var participants = participantRepository.findByChatRoomId(event.getChatroomId());
        for (var participant : participants) {
            if (participant.getUid().equals(event.getSenderId())) {
                continue; // Skip sender
            }

            // Upsert receipt
            var existing = receiptRepository.findByMessageIdAndUid(
                    event.getMessageId(), participant.getUid());
            if (existing.isEmpty()) {
                receiptRepository.save(MessageReceipt.builder()
                        .messageId(event.getMessageId())
                        .uid(participant.getUid())
                        .deliveredAt(deliveredAt)
                        .build());
                log.debug("Delivery receipt created: messageId={}, uid={}",
                        event.getMessageId(), participant.getUid());
            }
        }

        log.info("✅ Message delivery processed: messageId={}", event.getMessageId());
    }
}
