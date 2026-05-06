package com.trading.aiscalptrader.safety;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.data.MarketDataBus;
import com.trading.aiscalptrader.domain.model.Tick;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/** Safety #12 — halt trading if any index moves >3% in last 60 seconds. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashCrashProtector {

    private final AutoScalpProperties props;
    private final MarketDataBus bus;

    private final Map<Long, Deque<TimedPrice>> windows = new HashMap<>();
    private final AtomicBoolean halted = new AtomicBoolean(false);

    private record TimedPrice(long ts, double price) {}

    @PostConstruct
    public void init() {
        bus.subscribeTicks(this::onTick);
    }

    private void onTick(Tick tick) {
        Deque<TimedPrice> w = windows.computeIfAbsent(tick.instrumentToken(), k -> new ConcurrentLinkedDeque<>());
        long now = tick.timestamp().toEpochMilli();
        w.offerLast(new TimedPrice(now, tick.lastPrice()));
        long cutoff = now - 60_000;
        while (!w.isEmpty() && w.peekFirst().ts < cutoff) w.pollFirst();
        if (w.size() < 5) return;

        double max = w.stream().mapToDouble(TimedPrice::price).max().orElse(0);
        double min = w.stream().mapToDouble(TimedPrice::price).min().orElse(0);
        if (min <= 0) return;
        double move = (max - min) / min;
        if (move > props.getRisk().getFlashCrashPct() && halted.compareAndSet(false, true)) {
            log.error("[FLASH CRASH] Detected {}% swing in 60s on {}", String.format("%.2f", move * 100),
                    tick.instrumentToken());
        }
    }

    public boolean isHalted() { return halted.get(); }
    public void reset() { halted.set(false); }
}
