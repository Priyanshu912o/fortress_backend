package com.fortress.chat.ratelimit;

/**
 * Token Bucket — hand-rolled rate limiter implementation.
 *
 * How it works:
 * - Each bucket has a maximum capacity (maxTokens) and refills at a constant rate.
 * - On each request, tryConsume() first refills tokens based on elapsed time,
 *   then checks if at least 1 token is available.
 * - If available, it decrements and returns true (allow). Otherwise false (reject).
 *
 * This is intentionally implemented from scratch (no library) to be explainable
 * line-by-line in a technical interview.
 *
 * Thread safety: all public methods are synchronized on the instance.
 */
public class TokenBucket {

    private double tokens;
    private final double maxTokens;
    private final double refillRatePerSecond; // tokens added per second
    private long lastRefillNanos;

    public TokenBucket(double maxTokens, double refillRatePerSecond) {
        this.maxTokens = maxTokens;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = maxTokens; // Start full
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Try to consume one token.
     * @return true if the request is allowed, false if rate-limited
     */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Refill tokens based on elapsed time since last refill.
     * Uses nanosecond precision to avoid time drift.
     */
    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        double tokensToAdd = elapsedSeconds * refillRatePerSecond;

        tokens = Math.min(maxTokens, tokens + tokensToAdd);
        lastRefillNanos = now;
    }

    /**
     * Estimated seconds until the next token is available.
     * Used for the Retry-After header.
     */
    public synchronized int getRetryAfterSeconds() {
        refill();
        if (tokens >= 1.0) return 0;
        double deficit = 1.0 - tokens;
        return (int) Math.ceil(deficit / refillRatePerSecond);
    }

    /**
     * @return nanosecond timestamp of last refill (used for stale bucket eviction)
     */
    public synchronized long getLastRefillNanos() {
        return lastRefillNanos;
    }
}
