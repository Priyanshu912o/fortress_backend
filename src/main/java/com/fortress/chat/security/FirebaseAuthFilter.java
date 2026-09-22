package com.fortress.chat.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

/**
 * Firebase Auth filter — verifies Firebase ID tokens on every request.
 * Mirrors the existing Fastify auth plugin behavior.
 *
 * Supports dev bypass: if DEV_BYPASS_AUTH=true, tokens starting with "mock_"
 * are accepted with the remainder as the UID (matching existing behavior).
 */
@Component
@Slf4j
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final boolean devBypass;

    public FirebaseAuthFilter() {
        this.devBypass = "true".equalsIgnoreCase(System.getenv("DEV_BYPASS_AUTH"));
        if (devBypass) {
            log.warn("⚠️ DEV_BYPASS_AUTH is enabled — mock tokens accepted");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip auth for health check and WebSocket upgrade (WS auth handled separately)
        return path.equals("/health") ||
               path.startsWith("/ws");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            String uid;
            String email;

            // Dev bypass for testing (matches existing Fastify behavior)
            if (devBypass && token.startsWith("mock_")) {
                uid = token.substring(5); // Remove "mock_" prefix
                email = uid + "@fortress.test";
                log.debug("Dev bypass auth for uid: {}", uid);
            } else {
                FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
                uid = decoded.getUid();
                email = decoded.getEmail();
            }

            // Set authentication in SecurityContext
            FirebaseTokenHolder principal = new FirebaseTokenHolder(uid, email);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Also set as request attributes for easy access in controllers
            request.setAttribute("uid", uid);
            request.setAttribute("email", email);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("Token verification failed: {}", e.getMessage());
            sendUnauthorized(response, "Unauthorized: Invalid or expired Firebase ID token");
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"statusCode\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}", message));
    }
}
