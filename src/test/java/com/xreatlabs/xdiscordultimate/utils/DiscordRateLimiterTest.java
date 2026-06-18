package com.xreatlabs.xdiscordultimate.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DiscordRateLimiter.
 */
public class DiscordRateLimiterTest {

    @Test
    void testRateLimiterInitialization() {
        DiscordRateLimiter limiter = new DiscordRateLimiter(50, 5);
        assertNotNull(limiter, "Rate limiter should be initialized");
    }

    @Test
    void testTryAcquire() {
        DiscordRateLimiter limiter = new DiscordRateLimiter(10, 5);

        // Should allow first request
        assertTrue(limiter.tryAcquire("test-key"), "First request should be allowed");

        // Should allow multiple requests up to limit
        for (int i = 0; i < 4; i++) {
            assertTrue(limiter.tryAcquire("test-key"), "Request " + (i + 1) + " should be allowed");
        }

        // Wait briefly to refill
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            fail("Sleep interrupted");
        }

        // Should allow request after refill
        assertTrue(limiter.tryAcquire("test-key"), "Request after refill should be allowed");
    }

    @Test
    void testDifferentKeys() {
        DiscordRateLimiter limiter = new DiscordRateLimiter(2, 1);

        // Different keys should have independent rate limits
        assertTrue(limiter.tryAcquire("key1"), "Key1 request 1 should be allowed");
        assertTrue(limiter.tryAcquire("key2"), "Key2 request 1 should be allowed");
    }

    @Test
    void testReset() {
        DiscordRateLimiter limiter = new DiscordRateLimiter(1, 1);

        // Use up the rate limit
        assertTrue(limiter.tryAcquire("test-key"), "First request should be allowed");

        // Reset the rate limiter
        limiter.reset("test-key");

        // Should allow request after reset
        assertTrue(limiter.tryAcquire("test-key"), "Request after reset should be allowed");
    }

    @Test
    void testClear() {
        DiscordRateLimiter limiter = new DiscordRateLimiter(1, 1);

        assertTrue(limiter.tryAcquire("test-key"), "First request should be allowed");

        // Clear all buckets
        limiter.clear();

        // Should allow request after clear
        assertTrue(limiter.tryAcquire("test-key"), "Request after clear should be allowed");
    }

    @Test
    void testGetRemainingTokens() {
        DiscordRateLimiter limiter = new DiscordRateLimiter(5, 5);

        int initial = limiter.getRemainingTokens("new-key");
        assertTrue(initial > 0, "Should have remaining tokens");

        // After some requests, tokens should decrease
        limiter.tryAcquire("new-key");
        int afterRequest = limiter.getRemainingTokens("new-key");
        assertTrue(afterRequest < initial, "Tokens should decrease after request");
    }
}
