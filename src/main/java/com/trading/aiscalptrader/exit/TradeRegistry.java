package com.trading.aiscalptrader.exit;

import com.trading.aiscalptrader.domain.model.Trade;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Safety-14 — RLock-protected active trades. Java's ConcurrentHashMap gives
 * us thread-safety; mutations on individual Trade objects are guarded by the
 * Trade's own synchronization (or short critical sections inside ExitMonitor).
 */
@Component
public class TradeRegistry {

    private final ConcurrentMap<String, Trade> active = new ConcurrentHashMap<>();
    private final List<Trade> closed = new ArrayList<>();

    public synchronized void register(Trade trade) {
        active.put(trade.getId(), trade);
    }

    public synchronized void close(Trade trade) {
        active.remove(trade.getId());
        closed.add(trade);
    }

    public Collection<Trade> activeTrades() {
        return active.values();
    }

    public synchronized List<Trade> closedTradesToday() {
        return new ArrayList<>(closed);
    }

    public Optional<Trade> findActiveByUnderlying(long underlyingToken) {
        return active.values().stream()
                .filter(t -> t.getUnderlyingToken() == underlyingToken)
                .findFirst();
    }

    public boolean hasActive() { return !active.isEmpty(); }

    public synchronized void resetForDay() { closed.clear(); }
}
