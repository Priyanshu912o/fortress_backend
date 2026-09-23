package com.fortress.chat.controller;

import com.fortress.chat.dto.response.MessageResponse;
import com.fortress.chat.service.MessageService;
import com.fortress.chat.websocket.RoomManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final RoomManager roomManager;

    /** POST /api/messages — Send a message (rate-limited) */
    @PostMapping("/api/messages")
    public ResponseEntity<MessageResponse> sendMessage(HttpServletRequest request,
                                                        @RequestBody Map<String, String> body) {
        String currentUid = (String) request.getAttribute("uid");
        String chatRoomId = body.get("chatRoomId");
        String messageBody = body.get("body");
        String clientMessageId = body.get("clientMessageId");

        MessageResponse msg = messageService.sendMessage(currentUid, chatRoomId, messageBody, clientMessageId);
        return ResponseEntity.status(HttpStatus.CREATED).body(msg);
    }

    /** GET /api/messages?chatRoomId=... — Paginated message history (querystring format used by Flutter) */
    @GetMapping("/api/messages")
    public ResponseEntity<List<MessageResponse>> getHistoryByQuery(
            HttpServletRequest request,
            @RequestParam String chatRoomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(messageService.getHistory(chatRoomId, cursor, limit, currentUid));
    }

    /** GET /api/messages/{chatRoomId} — Paginated message history (path variable format) */
    @GetMapping("/api/messages/{chatRoomId}")
    public ResponseEntity<List<MessageResponse>> getHistory(HttpServletRequest request,
                                                             @PathVariable String chatRoomId,
                                                             @RequestParam(required = false) String cursor,
                                                             @RequestParam(defaultValue = "20") int limit) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(messageService.getHistory(chatRoomId, cursor, limit, currentUid));
    }

    /** POST /api/messages/{id}/delivered — Mark message as delivered */
    @PostMapping("/api/messages/{id}/delivered")
    public ResponseEntity<MessageResponse.ReceiptResponse> markDelivered(HttpServletRequest request,
                                                                          @PathVariable String id) {
        String currentUid = (String) request.getAttribute("uid");
        MessageResponse.ReceiptResponse receipt = messageService.markDelivered(id, currentUid);

        // Notify sender via WebSocket
        // (We'd need the message's senderId — get it from the receipt context)
        return ResponseEntity.ok(receipt);
    }

    /** POST /api/messages/read — Mark all messages in a room as read */
    @PostMapping("/api/messages/read")
    public ResponseEntity<Map<String, Object>> markRead(HttpServletRequest request,
                                                         @RequestBody Map<String, String> body) {
        String currentUid = (String) request.getAttribute("uid");
        String chatRoomId = body.get("chatRoomId");

        int count = messageService.markRead(chatRoomId, currentUid);

        // Broadcast read event via WebSocket
        roomManager.broadcastToRoom(chatRoomId, Map.of(
                "type", "message_read",
                "data", Map.of(
                        "chatRoomId", chatRoomId,
                        "uid", currentUid,
                        "readAt", java.time.Instant.now().toString()
                )
        ), null);

        return ResponseEntity.ok(Map.of("success", true, "count", count));
    }
}
