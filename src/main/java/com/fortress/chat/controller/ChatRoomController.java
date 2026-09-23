package com.fortress.chat.controller;

import com.fortress.chat.dto.response.ChatRoomResponse;
import com.fortress.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    /** GET /api/chat-rooms — List all chat rooms for current user */
    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> listChatRooms(HttpServletRequest request) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(chatRoomService.listForUser(currentUid));
    }

    /** POST /api/chat-rooms/direct — Get or create 1:1 direct chat room */
    @PostMapping("/direct")
    public ResponseEntity<ChatRoomResponse> getOrCreateDirect(HttpServletRequest request,
                                                               @RequestBody Map<String, String> body) {
        String currentUid = (String) request.getAttribute("uid");
        String otherUid = body.get("otherUid");
        return ResponseEntity.ok(chatRoomService.getOrCreateDirect(currentUid, otherUid));
    }

    /** POST /api/chat-rooms/group — Create a group chat room */
    @SuppressWarnings("unchecked")
    @PostMapping("/group")
    public ResponseEntity<ChatRoomResponse> createGroup(HttpServletRequest request,
                                                         @RequestBody Map<String, Object> body) {
        String currentUid = (String) request.getAttribute("uid");
        String name = (String) body.get("name");
        List<String> participantUids = (List<String>) body.get("participantUids");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                chatRoomService.createGroup(currentUid, name, participantUids));
    }

    /** GET /api/chat-rooms/{id} — Get chat room details */
    @GetMapping("/{id}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(HttpServletRequest request,
                                                         @PathVariable String id) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(chatRoomService.getById(id, currentUid));
    }
}
