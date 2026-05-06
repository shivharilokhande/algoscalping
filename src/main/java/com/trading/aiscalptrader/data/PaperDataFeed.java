package com.trading.aiscalptrader.data;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.TradingMode;
import com.trading.aiscalptrader.domain.model.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Paper-mode synthetic feed used when Kite credentials are absent.
 * Generates a random walk for each instrument so the rest of the pipeline
 * (candles, indicators, strategy) can be exercised without a broker.
 *
 * In production paper mode you'd replace this with a polling feed reading
 * Kite REST quotes (no WebSocket needed for low-frequency paper backtest).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperDataFeed {

    private final AutoScalpProperties props;
    private final DataEngine dataEngine;
    private final Random rng = new Random(42);

    /** Last simulated price per instrument */
    private final Map<Long, Double> last = new HashMap<>();
    /** Cumulative volume per instrument */
    private final Map<Long, Long> volume = new HashMap<>();

    private static final Map<Long, Double> SEED_PRICE = Map.of(
            256265L, 22000.0,
            260105L, 48000.0,
            257801L, 21000.0
    );

    @Scheduled(fixedDelay = 1000) // 1 tick per second per instrument
    public void emitTicks() {
        if (props.getSystem().getMode() != TradingMode.PAPER) return;
        if (props.getKite().hasCredentials()) return; // real paper feed connected elsewhere
        for (Long token : props.getSystem().getInstruments()) {
            double prev = last.getOrDefault(token, SEED_PRICE.getOrDefault(token, 20000.0));
            double pct = (rng.nextGaussian() * 0.0005); // ~5bp std per tick
            double next = Math.max(1.0, prev * (1.0 + pct));
            long vol = volume.getOrDefault(token, 0L) + (long)(Math.abs(rng.nextGaussian()) * 5000);
            last.put(token, next);
            volume.put(token, vol);
            Tick t = new Tick(token, next, vol, next * 0.9999, next * 1.0001, 100, 100, Instant.now());
            dataEngine.onTick(t);
        }
    }
}
