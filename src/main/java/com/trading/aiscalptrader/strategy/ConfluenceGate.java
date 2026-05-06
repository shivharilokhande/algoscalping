package com.trading.aiscalptrader.strategy;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.Signal;
import com.trading.aiscalptrader.domain.enums.SetupType;
import com.trading.aiscalptrader.domain.strategy.SetupVote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Layer 1 — keeps a rolling 5-bar window of setup votes per instrument.
 * Returns the dominant direction if ≥ minConfluenceSetups unique setups agree.
 */
@Component
@RequiredArgsConstructor
public class ConfluenceGate {

    private final AutoScalpProperties props;
    private final Map<Long, Deque<SetupVote>> window = new HashMap<>();

    public synchronized void record(long instrumentToken, SetupVote vote, long currentBarIndex) {
        Deque<SetupVote> q = window.computeIfAbsent(instrumentToken, k -> new ArrayDeque<>());
        q.offerLast(vote);
        // Trim entries older than `confluenceWindowBars`
        long minBar = currentBarIndex - props.getStrategy().getConfluenceWindowBars();
        while (!q.isEmpty() && q.peekFirst().barIndex() < minBar) q.pollFirst();
    }

    public synchronized Result evaluate(long instrumentToken, long currentBarIndex) {
        Deque<SetupVote> q = window.get(instrumentToken);
        if (q == null) return Result.empty();
        long minBar = currentBarIndex - props.getStrategy().getConfluenceWindowBars();
        Set<SetupType> ce = new HashSet<>();
        Set<SetupType> pe = new HashSet<>();
        double ceConfMax = 0, peConfMax = 0;
        for (SetupVote v : q) {
            if (v.barIndex() < minBar) continue;
            if (v.direction() == Signal.BUY_CE) {
                ce.add(v.setup());
                if (v.confidence() > ceConfMax) ceConfMax = v.confidence();
            } else if (v.direction() == Signal.BUY_PE) {
                pe.add(v.setup());
                if (v.confidence() > peConfMax) peConfMax = v.confidence();
            }
        }
        int min = props.getStrategy().getMinConfluenceSetups();
        if (ce.size() >= min && ce.size() >= pe.size()) {
            return new Result(Signal.BUY_CE, ce, ceConfMax);
        } else if (pe.size() >= min) {
            return new Result(Signal.BUY_PE, pe, peConfMax);
        }
        return Result.empty();
    }

    public synchronized void resetForDay() {
        window.clear();
    }

    public record Result(Signal direction, Set<SetupType> confluentSetups, double maxConfidence) {
        public boolean passed() { return direction != Signal.HOLD; }
        public static Result empty() {
            return new Result(Signal.HOLD, Set.of(), 0.0);
        }
    }
}
