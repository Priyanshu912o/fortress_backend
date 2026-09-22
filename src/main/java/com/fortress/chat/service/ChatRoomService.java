package com.fortress.chat.service;

import com.fortress.chat.dto.response.ChatRoomResponse;
import com.fortress.chat.dto.response.UserResponse;
import com.fortress.chat.entity.ChatRoom;
import com.fortress.chat.entity.ChatRoomParticipant;
import com.fortress.chat.entity.enums.ParticipantRole;
import com.fortress.chat.exception.*;
import com.fortress.chat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;

    /**
     * Get or create a direct (1:1) chat room between two users.
     * Enforces trust handshake: connection must be ACCEPTED.
     */
    @Transactional
    public ChatRoomResponse getOrCreateDirect(String currentUid, String otherUid) {
        if (otherUid.equals(currentUid)) {
            throw new BadRequestException("Cannot create direct chat with yourself");
        }

        // Enforce connection
        if (!connectionRepository.existsAcceptedBetween(currentUid, otherUid)) {
            throw new ForbiddenException(
                    "Cannot open chat: You must have an accepted connection request with this user");
        }

        // Check if room already exists
        Optional<ChatRoom> existing = chatRoomRepository.findDirectBetween(currentUid, otherUid);
        if (existing.isPresent()) {
            return toChatRoomResponse(existing.get(), currentUid);
        }

        // Create new direct room
        ChatRoom chatRoom = ChatRoom.builder()
                .isDirect(true)
                .createdAt(Instant.now())
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);

        // Add participants
        participantRepository.save(ChatRoomParticipant.builder()
                .chatRoomId(chatRoom.getId())
                .uid(currentUid)
                .role(ParticipantRole.MEMBER)
                .joinedAt(Instant.now())
                .build());
        participantRepository.save(ChatRoomParticipant.builder()
                .chatRoomId(chatRoom.getId())
                .uid(otherUid)
                .role(ParticipantRole.MEMBER)
                .joinedAt(Instant.now())
                .build());

        return toChatRoomResponse(chatRoom, currentUid);
    }

    /**
     * Create a group chat room.
     * All participants must have accepted connections with the creator.
     */
    @Transactional
    public ChatRoomResponse createGroup(String currentUid, String name, List<String> participantUids) {
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("Group name is required");
        }
        if (participantUids == null || participantUids.isEmpty()) {
            throw new BadRequestException("At least one participant must be added");
        }

        // Deduplicate and exclude creator
        Set<String> uniqueUids = participantUids.stream()
                .filter(uid -> !uid.equals(currentUid))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Validate all are connected
        var acceptedConnections = connectionRepository.findAcceptedConnectionsWithUsers(
                currentUid, new ArrayList<>(uniqueUids));
        Set<String> acceptedContactUids = acceptedConnections.stream()
                .map(c -> c.getFromUid().equals(currentUid) ? c.getToUid() : c.getFromUid())
                .collect(Collectors.toSet());

        List<String> nonContacts = uniqueUids.stream()
                .filter(uid -> !acceptedContactUids.contains(uid))
                .collect(Collectors.toList());
        if (!nonContacts.isEmpty()) {
            throw new ForbiddenException(
                    "You can only add your accepted contacts. Users not connected: " +
                    String.join(", ", nonContacts));
        }

        // Create group room
        ChatRoom chatRoom = ChatRoom.builder()
                .isDirect(false)
                .groupName(name.trim())
                .createdAt(Instant.now())
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);

        // Add creator as ADMIN
        participantRepository.save(ChatRoomParticipant.builder()
                .chatRoomId(chatRoom.getId())
                .uid(currentUid)
                .role(ParticipantRole.ADMIN)
                .joinedAt(Instant.now())
                .build());

        // Add other participants as MEMBER
        for (String uid : uniqueUids) {
            participantRepository.save(ChatRoomParticipant.builder()
                    .chatRoomId(chatRoom.getId())
                    .uid(uid)
                    .role(ParticipantRole.MEMBER)
                    .joinedAt(Instant.now())
                    .build());
        }

        return toChatRoomResponse(chatRoom, currentUid);
    }

    /**
     * List all chat rooms for the current user.
     */
    public List<ChatRoomResponse> listForUser(String uid) {
        return chatRoomRepository.findAllByParticipantUid(uid).stream()
                .map(room -> toChatRoomResponse(room, uid))
                .collect(Collectors.toList());
    }

    /**
     * Get a chat room by ID (with membership check).
     */
    public ChatRoomResponse getById(String chatRoomId, String currentUid) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));

        if (!participantRepository.existsByChatRoomIdAndUid(chatRoomId, currentUid)) {
            throw new ForbiddenException("You are not a participant in this chat room");
        }

        return toChatRoomResponse(room, currentUid);
    }

    /**
     * Build a ChatRoomResponse with participants and unread count.
     */
    public ChatRoomResponse toChatRoomResponse(ChatRoom room, String currentUid) {
        List<ChatRoomParticipant> participants = participantRepository.findByChatRoomId(room.getId());

        Integer unreadCount = 0;
        List<ChatRoomResponse.ParticipantResponse> participantResponses = new ArrayList<>();

        for (ChatRoomParticipant p : participants) {
            if (p.getUid().equals(currentUid)) {
                unreadCount = p.getUnreadCount();
            }

            UserResponse userResp = userRepository.findByUid(p.getUid())
                    .map(UserService::toResponse)
                    .orElse(null);

            participantResponses.add(ChatRoomResponse.ParticipantResponse.builder()
                    .id(p.getId())
                    .chatRoomId(p.getChatRoomId())
                    .uid(p.getUid())
                    .role(p.getRole().name())
                    .unreadCount(p.getUnreadCount())
                    .joinedAt(p.getJoinedAt())
                    .user(userResp)
                    .build());
        }

        return ChatRoomResponse.builder()
                .id(room.getId())
                .isDirect(room.getIsDirect())
                .groupName(room.getGroupName())
                .groupDpUrl(room.getGroupDpUrl())
                .lastMessageBody(room.getLastMessageBody())
                .lastMessageAt(room.getLastMessageAt())
                .lastSenderId(room.getLastSenderId())
                .createdAt(room.getCreatedAt())
                .unreadCount(unreadCount)
                .participants(participantResponses)
                .build();
    }
}
