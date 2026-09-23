package com.fortress.chat.controller;

import com.fortress.chat.dto.response.ConnectionResponse;
import com.fortress.chat.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    /** POST /api/connections — Send connection request */
    @PostMapping
    public ResponseEntity<ConnectionResponse> sendRequest(HttpServletRequest request,
                                                           @RequestBody Map<String, String> body) {
        String currentUid = (String) request.getAttribute("uid");
        String toUid = body.get("toUid");
        ConnectionResponse resp = connectionService.sendRequest(currentUid, toUid);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /** GET /api/connections — List accepted connections (contacts) */
    @GetMapping
    public ResponseEntity<List<ConnectionResponse>> listAccepted(HttpServletRequest request) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(connectionService.listAccepted(currentUid));
    }

    /** GET /api/connections/pending — List incoming pending requests */
    @GetMapping("/pending")
    public ResponseEntity<List<ConnectionResponse>> listPending(HttpServletRequest request) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(connectionService.listPending(currentUid));
    }

    /** GET /api/connections/sent — List outgoing sent requests */
    @GetMapping("/sent")
    public ResponseEntity<List<ConnectionResponse>> listSent(HttpServletRequest request) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(connectionService.listSent(currentUid));
    }

    /** PATCH /api/connections/{id}/accept */
    @PatchMapping("/{id}/accept")
    public ResponseEntity<ConnectionResponse> accept(HttpServletRequest request,
                                                      @PathVariable String id) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(connectionService.accept(id, currentUid));
    }

    /** PATCH /api/connections/{id}/reject */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ConnectionResponse> reject(HttpServletRequest request,
                                                      @PathVariable String id) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(connectionService.reject(id, currentUid));
    }

    /** PATCH /api/connections/{id}/block */
    @PatchMapping("/{id}/block")
    public ResponseEntity<ConnectionResponse> block(HttpServletRequest request,
                                                     @PathVariable String id) {
        String currentUid = (String) request.getAttribute("uid");
        return ResponseEntity.ok(connectionService.block(id, currentUid));
    }
}
