package com.fortress.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortress.chat.repository.ChatRoomParticipantRepository;
import com.fortress.chat.repository.ConnectionRepository;
import com.fortress.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RoomManager — in-memory registry mapping users to WebSocket sessions.
 * Mirrors the TypeScript RoomManager from the existing Fastify backend.
 *
 * Responsibilities:
 * - Track which sessions belong to which user (supports multiple devices)
 * - Broadcast events to all members of a chat room
 * - Send targeted events to specific users
 * - Manage online/offline presence
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomManager {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocketSession, String> sessionToUser = new ConcurrentHashMap<>();

    private final ChatRoomParticipantRepository participantRepository;
    private final ConnectionRepository connectionRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * Register a user's WebSocket session.
     */
    public void registerUser(String uid, WebSocketSession session) {
        userSessions.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToUser.put(session, uid);

        // Send authenticated ack
        sendToSession(session, Map.of("type", "authenticated", "uid", uid));

        // Mark online if first session
        if (userSessions.get(uid).size() == 1) {
            userService.setPresence(uid, true);
            broadcastPresence(uid, true);
        }

        log.info("WebSocket registered: uid={}, sessions={}", uid, userSessions.get(uid).size());
    }

    /**
     * Unregister a WebSocket session.
     */
    public String unregisterSession(WebSocketSession session) {
        String uid = sessionToUser.remove(session);
        if (uid == null) return null;

        Set<WebSocketSession> sessions = userSessions.get(uid);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(uid);
                userService.setPresence(uid, false);
                broadcastPresence(uid, false);
            }
        }

        log.info("WebSocket unregistered: uid={}", uid);
        return uid;
    }

    /**
     * Get the UID associated with a session.
     */
    public String getUid(WebSocketSession session) {
        return sessionToUser.get(session);
    }

    /**
     * Send an event to all sessions of a user.
     */
    public void sendToUser(String uid, Object event) {
        Set<WebSocketSession> sessions = userSessions.get(uid);
        if (sessions == null) return;

        String payload = toJson(event);
        for (WebSocketSession session : sessions) {
            sendRaw(session, payload);
        }
    }

    /**
     * Send an event to a specific session.
     */
    public void sendToSession(WebSocketSession session, Object event) {
        sendRaw(session, toJson(event));
    }

    /**
     * Broadcast an event to all members of a chat room.
     */
    public void broadcastToRoom(String chatRoomId, Object event, String excludeUid) {
        try {
            var participants = participantRepository.findByChatRoomId(chatRoomId);
            String payload = toJson(event);

            for (var participant : participants) {
                if (excludeUid != null && participant.getUid().equals(excludeUid)) continue;
                Set<WebSocketSession> sessions = userSessions.get(participant.getUid());
                if (sessions != null) {
                    for (WebSocketSession session : sessions) {
                        sendRaw(session, payload);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to broadcast to room {}: {}", chatRoomId, e.getMessage());
        }
    }

    /**
     * Broadcast presence change to the user's contacts.
     */
    private void broadcastPresence(String uid, boolean isOnline) {
        try {
            var connections = connectionRepository.findAllAcceptedForUser(uid);
            Map<String, Object> event = Map.of(
                    "type", "presence",
                    "data", Map.of(
                            "uid", uid,
                            "isOnline", isOnline,
                            "lastSeen", java.time.Instant.now().toString()
                    )
            );

            for (var conn : connections) {
                String contactUid = conn.getFromUid().equals(uid) ? conn.getToUid() : conn.getFromUid();
                sendToUser(contactUid, event);
            }
        } catch (Exception e) {
            log.error("Failed to broadcast presence for {}: {}", uid, e.getMessage());
        }
    }

    public boolean isUserOnline(String uid) {
        Set<WebSocketSession> sessions = userSessions.get(uid);
        return sessions != null && !sessions.isEmpty();
    }

    private void sendRaw(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.warn("Failed to send WS message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize WS event: {}", e.getMessage());
            return "{}";
        }
    }
}
