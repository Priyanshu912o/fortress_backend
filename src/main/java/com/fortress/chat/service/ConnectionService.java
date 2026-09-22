package com.fortress.chat.service;

import com.fortress.chat.dto.response.ConnectionResponse;
import com.fortress.chat.dto.response.UserResponse;
import com.fortress.chat.entity.Connection;
import com.fortress.chat.entity.enums.ConnectionStatus;
import com.fortress.chat.exception.*;
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
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final ChatRoomService chatRoomService;
    private final org.springframework.beans.factory.ObjectProvider<com.fortress.chat.websocket.RoomManager> roomManagerProvider;

    /**
     * Send a connection request.
     */
    @Transactional
    public ConnectionResponse sendRequest(String fromUid, String toUid) {
        if (toUid.equals(fromUid)) {
            throw new BadRequestException("Cannot send connection request to yourself");
        }

        // Check sender exists (must be synced first)
        userRepository.findByUid(fromUid)
                .orElseThrow(() -> new BadRequestException("Sender profile not synced yet. Please restart the app."));

        // Check recipient exists
        userRepository.findByUid(toUid)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient user not found"));

        // Check existing connection
        var existing = connectionRepository.findBetweenUsers(fromUid, toUid);
        if (existing.isPresent()) {
            Connection conn = existing.get();
            if (conn.getStatus() == ConnectionStatus.BLOCKED) {
                throw new ForbiddenException("Connection is blocked");
            }
            // Return existing if PENDING or ACCEPTED
            return toResponse(conn);
        }

        // Create new
        Connection connection = Connection.builder()
                .fromUid(fromUid)
                .toUid(toUid)
                .status(ConnectionStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        connection = connectionRepository.save(connection);

        ConnectionResponse resp = toResponseWithUsers(connection);
        try {
            com.fortress.chat.websocket.RoomManager rm = roomManagerProvider.getIfAvailable();
            if (rm != null) {
                rm.sendToUser(toUid, java.util.Map.of("type", "connection_request", "data", resp));
            }
        } catch (Exception e) {
            log.warn("Failed to send WS event for connection request: {}", e.getMessage());
        }

        return resp;
    }

    /**
     * Accept a connection request. Auto-creates a direct chat room.
     */
    @Transactional
    public ConnectionResponse accept(String connectionId, String currentUid) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection request not found"));

        if (!connection.getToUid().equals(currentUid)) {
            throw new ForbiddenException("Only the recipient can accept this connection request");
        }

        connection.setStatus(ConnectionStatus.ACCEPTED);
        connection.setUpdatedAt(Instant.now());
        connection = connectionRepository.save(connection);

        // Auto-create direct chat room
        var room = chatRoomService.getOrCreateDirect(currentUid, connection.getFromUid());

        ConnectionResponse resp = toResponseWithUsers(connection);
        resp.setChatRoomId(room.getId());
        try {
            com.fortress.chat.websocket.RoomManager rm = roomManagerProvider.getIfAvailable();
            if (rm != null) {
                rm.sendToUser(connection.getFromUid(), java.util.Map.of("type", "connection_accepted", "data", resp));
            }
        } catch (Exception e) {
            log.warn("Failed to send WS event for connection accepted: {}", e.getMessage());
        }

        return resp;
    }

    /**
     * Reject a connection request.
     */
    @Transactional
    public ConnectionResponse reject(String connectionId, String currentUid) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection request not found"));

        if (!connection.getToUid().equals(currentUid)) {
            throw new ForbiddenException("Only the recipient can reject this connection request");
        }

        connection.setStatus(ConnectionStatus.REJECTED);
        connection.setUpdatedAt(Instant.now());
        connection = connectionRepository.save(connection);

        return toResponse(connection);
    }

    /**
     * Block a user.
     */
    @Transactional
    public ConnectionResponse block(String connectionId, String currentUid) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection request not found"));

        if (!connection.getFromUid().equals(currentUid) && !connection.getToUid().equals(currentUid)) {
            throw new ForbiddenException("Unauthorized");
        }

        connection.setStatus(ConnectionStatus.BLOCKED);
        connection.setUpdatedAt(Instant.now());
        connection = connectionRepository.save(connection);

        return toResponse(connection);
    }

    /** List accepted connections (contacts) */
    public List<ConnectionResponse> listAccepted(String uid) {
        return connectionRepository.findAllAcceptedForUser(uid).stream()
                .map(this::toResponseWithUsers)
                .collect(Collectors.toList());
    }

    /** List incoming pending requests */
    public List<ConnectionResponse> listPending(String uid) {
        return connectionRepository.findByToUidAndStatusOrderByCreatedAtDesc(uid, ConnectionStatus.PENDING)
                .stream()
                .map(this::toResponseWithUsers)
                .collect(Collectors.toList());
    }

    /** List outgoing sent requests */
    public List<ConnectionResponse> listSent(String uid) {
        return connectionRepository.findByFromUidAndStatusOrderByCreatedAtDesc(uid, ConnectionStatus.PENDING)
                .stream()
                .map(this::toResponseWithUsers)
                .collect(Collectors.toList());
    }

    private ConnectionResponse toResponse(Connection c) {
        return ConnectionResponse.builder()
                .id(c.getId())
                .fromUid(c.getFromUid())
                .toUid(c.getToUid())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private ConnectionResponse toResponseWithUsers(Connection c) {
        ConnectionResponse resp = toResponse(c);
        userRepository.findByUid(c.getFromUid()).ifPresent(u -> resp.setFromUser(UserService.toResponse(u)));
        userRepository.findByUid(c.getToUid()).ifPresent(u -> resp.setToUser(UserService.toResponse(u)));
        return resp;
    }
}
