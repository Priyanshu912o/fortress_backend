package com.fortress.chat.ratelimit;

import com.fortress.chat.security.FirebaseTokenHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rate limit interceptor — applied to specific endpoints via WebMvcConfig.
 *
 * POST /api/messages → per-user rate limit (prevents message flooding)
 * POST /api/users/sync → per-IP rate limit (prevents brute-force sync)
 *
 * Returns 429 Too Many Requests with Retry-After header when limit exceeded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Rate limit POST /api/messages — per-user
        if ("POST".equals(method) && path.equals("/api/messages")) {
            return checkMessageRateLimit(request, response);
        }

        // Rate limit POST /api/users/sync — per-IP
        if ("POST".equals(method) && path.equals("/api/users/sync")) {
            return checkSyncRateLimit(request, response);
        }

        return true; // Not rate-limited
    }

    private boolean checkMessageRateLimit(HttpServletRequest request, HttpServletResponse response) throws Exception {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof FirebaseTokenHolder holder)) {
            return true; // Let auth filter handle
        }

        String userId = holder.getUid();
        if (!rateLimiterService.allowMessageSend(userId)) {
            int retryAfter = rateLimiterService.getMessageRetryAfter(userId);
            log.warn("Rate limit exceeded for user {} on POST /api/messages", userId);
            sendRateLimitResponse(response, retryAfter);
            return false;
        }
        return true;
    }

    private boolean checkSyncRateLimit(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String ip = getClientIp(request);
        if (!rateLimiterService.allowSync(ip)) {
            int retryAfter = rateLimiterService.getSyncRetryAfter(ip);
            log.warn("Rate limit exceeded for IP {} on POST /api/users/sync", ip);
            sendRateLimitResponse(response, retryAfter);
            return false;
        }
        return true;
    }

    private void sendRateLimitResponse(HttpServletResponse response, int retryAfter) throws Exception {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"statusCode\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Retry after %d seconds.\"}", retryAfter));
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
