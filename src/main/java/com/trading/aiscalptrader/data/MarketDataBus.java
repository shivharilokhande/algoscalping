package com.trading.aiscalptrader.data;

import com.trading.aiscalptrader.domain.enums.CandleInterval;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.model.Tick;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publishes ticks and closed candles to subscribers.
 * Decouples DataEngine from StrategyEngine, ExitMonitor, SafetyLayer.
 */
@Slf4j
@Component
public class MarketDataBus {

    private final List<TickListener> tickListeners = new CopyOnWriteArrayList<>();
    private final List<CandleListener> candleListeners = new CopyOnWriteArrayList<>();

    public void subscribeTicks(TickListener l) { tickListeners.add(l); }
    public void subscribeCandles(CandleListener l) { candleListeners.add(l); }

    public void publishTick(Tick tick) {
        for (TickListener l : tickListeners) {
            try { l.onTick(tick); }
            catch (Exception e) { log.error("Tick listener {} failed: {}", l.getClass().getSimpleName(), e.getMessage(), e); }
        }
    }

    public void publishCandle(Candle candle) {
        for (CandleListener l : candleListeners) {
            try { l.onCandle(candle); }
            catch (Exception e) { log.error("Candle listener {} failed: {}", l.getClass().getSimpleName(), e.getMessage(), e); }
        }
    }

    @FunctionalInterface
    public interface TickListener { void onTick(Tick tick); }

    @FunctionalInterface
    public interface CandleListener {
        void onCandle(Candle candle);
        default boolean supports(CandleInterval interval) { return true; }
    }
}
