package com.fortress.chat.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortress.chat.dto.response.MessageResponse;
import com.fortress.chat.service.MessageService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Map;

/**
 * WebSocket handler — manages connection lifecycle and message routing.
 * Mirrors the existing Fastify WebSocket handler (ws/handler.ts).
 *
 * Auth flow:
 * 1. Client connects to /ws
 * 2. Client sends: { "type": "auth", "token": "<firebase-id-token>" }
 * 3. Server verifies token and registers the session
 * 4. Server sends: { "type": "authenticated", "uid": "..." }
 *
 * Client events: auth, send_message, mark_read, typing
 * Server events: authenticated, message_new, message_ack, message_delivered,
 *                message_read, presence, connection_request, error
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FortressWebSocketHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    private final boolean devBypass = "true".equalsIgnoreCase(System.getenv("DEV_BYPASS_AUTH"));

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection opened: sessionId={}", session.getId());
        // Wait for auth message before registering
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode event = objectMapper.readTree(message.getPayload());
            String type = event.has("type") ? event.get("type").asText() : "";

            switch (type) {
                case "authenticate", "auth" -> handleAuth(session, event);
                case "send_message" -> handleSendMessage(session, event);
                case "mark_read" -> handleMarkRead(session, event);
                case "typing", "typing_start", "typing_stop" -> handleTyping(session, event);
                case "ping" -> roomManager.sendToSession(session, Map.of("type", "pong"));
                default -> roomManager.sendToSession(session, Map.of(
                        "type", "error",
                        "message", "Unknown event type: " + type));
            }
        } catch (Exception e) {
            log.error("Error handling WS message: {}", e.getMessage());
            roomManager.sendToSession(session, Map.of(
                    "type", "error",
                    "message", "Internal error: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String uid = roomManager.unregisterSession(session);
        log.info("WebSocket connection closed: sessionId={}, uid={}, status={}", session.getId(), uid, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: sessionId={}, error={}", session.getId(), exception.getMessage());
        roomManager.unregisterSession(session);
    }

    // --- Event Handlers ---

    private void handleAuth(WebSocketSession session, JsonNode event) {
        String token = event.has("token") ? event.get("token").asText() : "";

        try {
            String uid;

            if (devBypass && token.startsWith("mock_")) {
                uid = token.substring(5);
                log.debug("WS dev bypass auth: uid={}", uid);
            } else {
                try {
                    FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
                    uid = decoded.getUid();
                } catch (Exception ex) {
                    log.warn("FirebaseAuth.verifyIdToken failed on WS: {}. Checking dev bypass fallback...", ex.getMessage());
                    if (devBypass) {
                        uid = parseUidFromJwt(token);
                        if (uid == null) throw ex;
                        log.info("Extracted UID {} via dev bypass fallback for WS", uid);
                    } else {
                        throw ex;
                    }
                }
            }

            roomManager.registerUser(uid, session);
            log.info("WS user registered successfully: uid={}, sessionId={}", uid, session.getId());

        } catch (Exception e) {
            log.warn("WS auth failed: {}", e.getMessage());
            roomManager.sendToSession(session, Map.of(
                    "type", "error",
                    "message", "Authentication failed: " + e.getMessage()));
        }
    }

    private String parseUidFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                JsonNode json = objectMapper.readTree(payload);
                if (json.has("user_id")) return json.get("user_id").asText();
                if (json.has("sub")) return json.get("sub").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void handleSendMessage(WebSocketSession session, JsonNode event) {
        String currentUid = roomManager.getUid(session);
        if (currentUid == null) {
            roomManager.sendToSession(session, Map.of("type", "error", "message", "Not authenticated"));
            return;
        }

        String chatRoomId = event.has("chatRoomId") ? event.get("chatRoomId").asText() : null;
        String body = event.has("body") ? event.get("body").asText() : null;
        String clientMessageId = event.has("clientMessageId") ? event.get("clientMessageId").asText() : null;

        try {
            MessageResponse msg = messageService.sendMessage(currentUid, chatRoomId, body, clientMessageId);

            // Ack to sender
            roomManager.sendToSession(session, Map.of(
                    "type", "message_ack",
                    "data", Map.of(
                            "clientMessageId", msg.getClientMessageId(),
                            "serverMessageId", msg.getId(),
                            "sentAt", msg.getSentAt().toString()
                    )
            ));

        } catch (Exception e) {
            roomManager.sendToSession(session, Map.of(
                    "type", "error",
                    "message", "Failed to send message: " + e.getMessage()));
        }
    }

    private void handleMarkRead(WebSocketSession session, JsonNode event) {
        String currentUid = roomManager.getUid(session);
        if (currentUid == null) return;

        String chatRoomId = event.has("chatRoomId") ? event.get("chatRoomId").asText() : null;
        if (chatRoomId == null) return;

        try {
            messageService.markRead(chatRoomId, currentUid);

            // Broadcast read event
            roomManager.broadcastToRoom(chatRoomId, Map.of(
                    "type", "message_read",
                    "data", Map.of(
                            "chatRoomId", chatRoomId,
                            "uid", currentUid,
                            "readAt", java.time.Instant.now().toString()
                    )
            ), null);
        } catch (Exception e) {
            roomManager.sendToSession(session, Map.of(
                    "type", "error",
                    "message", "Failed to mark read: " + e.getMessage()));
        }
    }

    private void handleTyping(WebSocketSession session, JsonNode event) {
        String currentUid = roomManager.getUid(session);
        if (currentUid == null) return;

        String chatRoomId = event.has("chatRoomId") ? event.get("chatRoomId").asText() : null;
        if (chatRoomId == null) return;

        String type = event.has("type") ? event.get("type").asText() : "";
        boolean isTyping = type.equals("typing_start") ||
                (event.has("isTyping") && event.get("isTyping").asBoolean());

        // Broadcast typing event (excluding sender)
        roomManager.broadcastToRoom(chatRoomId, Map.of(
                "type", "typing",
                "data", Map.of("chatRoomId", chatRoomId, "uid", currentUid, "isTyping", isTyping)
        ), currentUid);
    }
}
