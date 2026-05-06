package com.trading.aiscalptrader.strategy;

import com.trading.aiscalptrader.domain.strategy.HighWRResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Spec 4.7 — buffers signals from all 3 instruments fired on the same minute,
 * picks highest (confluence count desc, confidence desc).
 */
@Component
public class SignalBuffer {

    public record BufferedSignal(long instrumentToken, HighWRResult result, double ltp, Instant barTime) {}

    private final List<BufferedSignal> buffer = new ArrayList<>();

    public synchronized void add(BufferedSignal s) {
        buffer.add(s);
    }

    /** Pop the best signal (and clear the buffer). Returns empty if no signals. */
    public synchronized Optional<BufferedSignal> drainBest() {
        if (buffer.isEmpty()) return Optional.empty();
        BufferedSignal best = buffer.stream()
                .max(Comparator
                        .<BufferedSignal>comparingInt(s -> s.result().confluenceCount())
                        .thenComparingDouble(s -> s.result().confidence()))
                .orElse(null);
        buffer.clear();
        return Optional.ofNullable(best);
    }

    public synchronized int size() { return buffer.size(); }

    public synchronized void clear() { buffer.clear(); }
}
