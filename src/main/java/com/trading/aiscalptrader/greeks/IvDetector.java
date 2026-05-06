package com.trading.aiscalptrader.greeks;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * F-017 — rolling 20-day IV mean & stddev. is_elevated returns true when
 * current IV is z-score > configurable threshold above mean (default 1.3).
 */
@Component
public class IvDetector {

    private final Deque<Double> history = new ArrayDeque<>();
    private static final int WINDOW = 20;

    @Getter private double mean = 0;
    @Getter private double stddev = 0;

    public synchronized void update(double iv) {
        if (Double.isNaN(iv) || iv <= 0) return;
        if (history.size() >= WINDOW) history.pollFirst();
        history.offerLast(iv);
        recompute();
    }

    public synchronized boolean isElevated(double iv, double zThreshold) {
        if (history.size() < 5 || stddev <= 0) return false;
        double z = (iv - mean) / stddev;
        return z > zThreshold;
    }

    private void recompute() {
        double s = 0;
        for (Double v : history) s += v;
        mean = s / history.size();
        double var = 0;
        for (Double v : history) var += (v - mean) * (v - mean);
        stddev = Math.sqrt(var / history.size());
    }
}
