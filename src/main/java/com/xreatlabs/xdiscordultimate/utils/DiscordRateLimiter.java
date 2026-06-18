package com.xreatlabs.xdiscordultimate.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscordRateLimiter {

    private static final int DEFAULT_PER_MINUTE = 50;
    private static final int DEFAULT_PER_SECOND = 5;

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int defaultRequestsPerMinute;
    private final int defaultRequestsPerSecond;

    public DiscordRateLimiter() {
        this(DEFAULT_PER_MINUTE, DEFAULT_PER_SECOND);
    }

    public DiscordRateLimiter(int requestsPerMinute, int requestsPerSecond) {
        this.defaultRequestsPerMinute = requestsPerMinute;
        this.defaultRequestsPerSecond = requestsPerSecond;
    }

    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    public boolean tryAcquire(String key, int tokens) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(defaultRequestsPerSecond, defaultRequestsPerMinute));
        return bucket.tryConsume(tokens);
    }

    public void reset(String key) {
        buckets.remove(key);
    }

    public void clear() {
        buckets.clear();
    }

    public int getRemainingTokens(String key) {
        TokenBucket bucket = buckets.get(key);
        return bucket != null ? bucket.getRemaining() : defaultRequestsPerSecond;
    }

    private static class TokenBucket {
        private static final int SECOND_MS = 1000;
        private static final int MINUTE_MS = 60000;

        private final int maxPerSecond;
        private final int maxPerMinute;
        private final AtomicInteger tokensPerSecond;
        private final AtomicInteger tokensPerMinute;
        private volatile long lastRefillTime;
        private volatile long lastMinuteRefill;

        TokenBucket(int maxPerSecond, int maxPerMinute) {
            this.maxPerSecond = maxPerSecond;
            this.maxPerMinute = maxPerMinute;
            this.tokensPerSecond = new AtomicInteger(maxPerSecond);
            this.tokensPerMinute = new AtomicInteger(maxPerMinute);
            this.lastRefillTime = System.currentTimeMillis();
            this.lastMinuteRefill = System.currentTimeMillis();
        }

        boolean tryConsume(int tokens) {
            refill();

            int currentSecondTokens = tokensPerSecond.get();
            if (currentSecondTokens < tokens) {
                return false;
            }

            int currentMinuteTokens = tokensPerMinute.get();
            if (currentMinuteTokens < tokens) {
                return false;
            }

            if (tokensPerSecond.compareAndSet(currentSecondTokens, currentSecondTokens - tokens)) {
                tokensPerMinute.addAndGet(-tokens);
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsedSince = now - lastRefillTime;

            if (elapsedSince >= SECOND_MS) {
                int secondsPassed = (int) (elapsedSince / SECOND_MS);
                int toAdd = Math.min(secondsPassed * maxPerSecond, maxPerSecond);
                tokensPerSecond.set(Math.min(maxPerSecond, tokensPerSecond.get() + toAdd));
                lastRefillTime = now;
            }

            long minuteElapsed = now - lastMinuteRefill;
            if (minuteElapsed >= MINUTE_MS) {
                int minutesPassed = (int) (minuteElapsed / MINUTE_MS);
                int toAdd = Math.min(minutesPassed * maxPerMinute, maxPerMinute);
                tokensPerMinute.set(Math.min(maxPerMinute, tokensPerMinute.get() + toAdd));
                lastMinuteRefill = now;
            }
        }

        int getRemaining() {
            refill();
            return Math.min(tokensPerSecond.get(), tokensPerMinute.get());
        }
    }
}
