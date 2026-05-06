package com.trading.aiscalptrader.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Safety #1 — token-bucket limiter for the Kite API (max 10 req/s default).
 * Threadsafe, blocking acquire().
 */
@Slf4j
@Component
public class RateLimiter {

    private final double capacity = 10;       // tokens
    private final double refillPerSec = 10;
    private double tokens = 10;
    private long lastRefill = System.nanoTime();

    public synchronized void acquire() {
        long now = System.nanoTime();
        double elapsedSec = (now - lastRefill) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSec * refillPerSec);
        lastRefill = now;

        if (tokens < 1) {
            long waitMs = (long) Math.ceil((1.0 - tokens) / refillPerSec * 1000);
            try { Thread.sleep(waitMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            tokens = 0;
        } else {
            tokens -= 1;
        }
    }
}
