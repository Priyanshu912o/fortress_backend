package com.fortress.chat.service;

import com.fortress.chat.dto.response.MessageResponse;
import com.fortress.chat.dto.response.UserResponse;
import com.fortress.chat.entity.*;
import com.fortress.chat.exception.*;
import com.fortress.chat.kafka.MessageProducer;
import com.fortress.chat.kafka.MessageSentEvent;
import com.fortress.chat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final MessageProducer messageProducer;
    private final org.springframework.beans.factory.ObjectProvider<com.fortress.chat.websocket.RoomManager> roomManagerProvider;

    /**
     * Send a message — idempotent via clientMessageId.
     * After DB persist, publishes a MessageSentEvent to Kafka and broadcasts to WebSocket room.
     */
    @Transactional
    public MessageResponse sendMessage(String senderUid, String chatRoomId, String body, String clientMessageId) {
        if (chatRoomId == null || body == null || clientMessageId == null) {
            throw new BadRequestException("Fields \"chatRoomId\", \"body\", and \"clientMessageId\" are required");
        }

        // Verify membership
        if (!participantRepository.existsByChatRoomIdAndUid(chatRoomId, senderUid)) {
            throw new ForbiddenException("You are not a participant in this chat room");
        }

        // Idempotent: check if message already exists
        var existing = messageRepository.findByClientMessageId(clientMessageId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Instant now = Instant.now();

        // Create message
        Message message = Message.builder()
                .clientMessageId(clientMessageId)
                .chatRoomId(chatRoomId)
                .senderId(senderUid)
                .body(body)
                .sentAt(now)
                .build();
        message = messageRepository.save(message);

        // Update chatroom denormalized fields
        chatRoomRepository.findById(chatRoomId).ifPresent(room -> {
            room.setLastMessageBody(body);
            room.setLastMessageAt(now);
            room.setLastSenderId(senderUid);
            chatRoomRepository.save(room);
        });

        // Increment unread count for other participants
        participantRepository.incrementUnreadForOthers(chatRoomId, senderUid);

        // Publish to Kafka (async delivery)
        try {
            messageProducer.publish(new MessageSentEvent(
                    message.getId(),
                    chatRoomId,
                    senderUid,
                    body,
                    now
            ));
        } catch (Exception e) {
            log.warn("Failed to publish message to Kafka: {}", e.getMessage());
            // Don't fail the request — Kafka is for async delivery, not critical path
        }

        MessageResponse response = toResponse(message);

        // Broadcast real-time message_new event to the chat room via WebSocket
        try {
            com.fortress.chat.websocket.RoomManager rm = roomManagerProvider.getIfAvailable();
            if (rm != null) {
                rm.broadcastToRoom(chatRoomId, Map.of(
                        "type", "message_new",
                        "data", response
                ), senderUid);
                log.info("Broadcasted message_new to room {}: messageId={}", chatRoomId, message.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast message to room via WS: {}", e.getMessage());
        }

        return response;
    }

    /**
     * Paginated message history (cursor-based).
     */
    public List<MessageResponse> getHistory(String chatRoomId, String cursor, int limit, String currentUid) {
        // Verify membership
        if (!participantRepository.existsByChatRoomIdAndUid(chatRoomId, currentUid)) {
            throw new ForbiddenException("You are not a participant in this chat room");
        }

        PageRequest page = PageRequest.of(0, limit);
        List<Message> messages;

        if (cursor != null && !cursor.isBlank()) {
            Instant cursorTime = Instant.parse(cursor);
            messages = messageRepository.findByChatRoomIdAndDeletedAtIsNullAndSentAtBeforeOrderBySentAtDesc(
                    chatRoomId, cursorTime, page);
        } else {
            messages = messageRepository.findByChatRoomIdAndDeletedAtIsNullOrderBySentAtDesc(
                    chatRoomId, page);
        }

        return messages.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Mark a message as delivered.
     */
    @Transactional
    public MessageResponse.ReceiptResponse markDelivered(String messageId, String uid) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        Instant now = Instant.now();
        MessageReceipt receipt = receiptRepository.findByMessageIdAndUid(messageId, uid)
                .map(r -> {
                    r.setDeliveredAt(now);
                    return receiptRepository.save(r);
                })
                .orElseGet(() -> receiptRepository.save(MessageReceipt.builder()
                        .messageId(messageId)
                        .uid(uid)
                        .deliveredAt(now)
                        .build()));

        return toReceiptResponse(receipt);
    }

    /**
     * Mark all messages in a chat room as read.
     */
    @Transactional
    public int markRead(String chatRoomId, String currentUid) {
        // Reset unread count
        participantRepository.resetUnreadCount(chatRoomId, currentUid);

        Instant readAt = Instant.now();

        // Upsert read receipts for messages from other senders
        List<Message> messages = messageRepository.findByChatRoomIdAndSenderIdNot(chatRoomId, currentUid);
        for (Message msg : messages) {
            receiptRepository.findByMessageIdAndUid(msg.getId(), currentUid)
                    .ifPresentOrElse(
                            receipt -> {
                                receipt.setReadAt(readAt);
                                receiptRepository.save(receipt);
                            },
                            () -> receiptRepository.save(MessageReceipt.builder()
                                    .messageId(msg.getId())
                                    .uid(currentUid)
                                    .readAt(readAt)
                                    .deliveredAt(readAt)
                                    .build())
                    );
        }

        return messages.size();
    }

    public MessageResponse toResponse(Message msg) {
        UserResponse senderResp = userRepository.findByUid(msg.getSenderId())
                .map(UserService::toResponse)
                .orElse(null);

        List<MessageResponse.ReceiptResponse> receipts = receiptRepository.findByMessageId(msg.getId())
                .stream()
                .map(this::toReceiptResponse)
                .collect(Collectors.toList());

        return MessageResponse.builder()
                .id(msg.getId())
                .clientMessageId(msg.getClientMessageId())
                .chatRoomId(msg.getChatRoomId())
                .senderId(msg.getSenderId())
                .body(msg.getBody())
                .sentAt(msg.getSentAt())
                .deletedAt(msg.getDeletedAt())
                .sender(senderResp)
                .receipts(receipts)
                .build();
    }

    private MessageResponse.ReceiptResponse toReceiptResponse(MessageReceipt r) {
        return MessageResponse.ReceiptResponse.builder()
                .id(r.getId())
                .messageId(r.getMessageId())
                .uid(r.getUid())
                .deliveredAt(r.getDeliveredAt())
                .readAt(r.getReadAt())
                .build();
    }
}
