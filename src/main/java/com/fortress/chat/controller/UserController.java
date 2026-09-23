package com.fortress.chat.controller;

import com.fortress.chat.dto.response.UserResponse;
import com.fortress.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /api/users/sync — Sync/upsert user profile on login.
     * Mirrors existing Fastify route.
     */
    @PostMapping("/sync")
    public ResponseEntity<UserResponse> syncUser(HttpServletRequest request,
                                                  @RequestBody(required = false) Map<String, String> body) {
        String uid = (String) request.getAttribute("uid");
        String email = (String) request.getAttribute("email");
        String name = body != null ? body.get("name") : null;
        String dpUrl = body != null ? body.get("dpUrl") : null;

        return ResponseEntity.ok(userService.syncUser(uid, email, name, dpUrl));
    }

    /**
     * GET /api/users/search?email=xxx — Search users by email.
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(HttpServletRequest request,
                                                           @RequestParam String email) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(userService.searchByEmail(email, currentUid));
    }

    /**
     * GET /api/users/{uid} — Get user profile by UID.
     */
    @GetMapping("/{uid}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String uid) {
        return ResponseEntity.ok(userService.getUserByUid(uid));
    }

    /**
     * DELETE /api/users/me — Delete/tombstone the authenticated user's account.
     * Retains chat history for contacts, removes connections, deletes from Firebase Auth.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteAccount(HttpServletRequest request) {
        String currentUid = (String) request.getAttribute("uid");
        userService.deleteAccount(currentUid);
        return ResponseEntity.ok(Map.of(
                "status", "deleted",
                "message", "Account successfully anonymized and deleted"
        ));
    }
}
