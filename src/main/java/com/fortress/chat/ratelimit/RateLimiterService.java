package com.fortress.chat.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter service — manages per-key token buckets.
 *
 * Two bucket profiles:
 * 1. Message send: per-user, generous (10 tokens, 1/sec refill)
 * 2. Sync/login: per-IP, stricter (5 tokens, 1 per 10sec refill)
 *
 * Buckets are stored in-memory via ConcurrentHashMap. A production deployment
 * would use Redis instead — documented as a "next step" in the README.
 *
 * Stale buckets (no request in 10 minutes) are evicted by a scheduled task.
 */
@Service
@Slf4j
public class RateLimiterService {

    private final ConcurrentHashMap<String, TokenBucket> messageBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> syncBuckets = new ConcurrentHashMap<>();

    @Value("${ratelimit.message.max-tokens:10}")
    private double messageMaxTokens;

    @Value("${ratelimit.message.refill-rate:1.0}")
    private double messageRefillRate;

    @Value("${ratelimit.sync.max-tokens:5}")
    private double syncMaxTokens;

    @Value("${ratelimit.sync.refill-rate:0.1}")
    private double syncRefillRate;

    /**
     * Check rate limit for message sending (keyed by user UID).
     * @return true if allowed, false if rate-limited
     */
    public boolean allowMessageSend(String userId) {
        TokenBucket bucket = messageBuckets.computeIfAbsent(userId,
                k -> new TokenBucket(messageMaxTokens, messageRefillRate));
        return bucket.tryConsume();
    }

    /**
     * Check rate limit for user sync/login (keyed by IP address).
     * @return true if allowed, false if rate-limited
     */
    public boolean allowSync(String ipAddress) {
        TokenBucket bucket = syncBuckets.computeIfAbsent(ipAddress,
                k -> new TokenBucket(syncMaxTokens, syncRefillRate));
        return bucket.tryConsume();
    }

    /**
     * Get retry-after seconds for message send rate limit.
     */
    public int getMessageRetryAfter(String userId) {
        TokenBucket bucket = messageBuckets.get(userId);
        return bucket != null ? bucket.getRetryAfterSeconds() : 0;
    }

    /**
     * Get retry-after seconds for sync rate limit.
     */
    public int getSyncRetryAfter(String ipAddress) {
        TokenBucket bucket = syncBuckets.get(ipAddress);
        return bucket != null ? bucket.getRetryAfterSeconds() : 0;
    }

    /**
     * Evict stale buckets every 10 minutes.
     * A bucket is stale if its last refill was more than 10 minutes ago.
     */
    @Scheduled(fixedRate = 600_000) // 10 minutes
    public void evictStaleBuckets() {
        long cutoff = System.nanoTime() - (600L * 1_000_000_000L); // 10 min ago
        int messagePurged = evictFrom(messageBuckets, cutoff);
        int syncPurged = evictFrom(syncBuckets, cutoff);
        if (messagePurged + syncPurged > 0) {
            log.debug("Evicted {} message + {} sync stale rate-limit buckets", messagePurged, syncPurged);
        }
    }

    private int evictFrom(ConcurrentHashMap<String, TokenBucket> map, long cutoffNanos) {
        int count = 0;
        var iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().getLastRefillNanos() < cutoffNanos) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }
}
