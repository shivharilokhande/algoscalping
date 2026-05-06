package com.trading.aiscalptrader.domain.model;

import com.trading.aiscalptrader.domain.enums.CandleInterval;

import java.time.Instant;

/**
 * Immutable OHLCV candle. Built by CandleBuilder from raw ticks.
 * timestamp = candle close time (right-aligned).
 */
public record Candle(
        long instrumentToken,
        CandleInterval interval,
        Instant openTime,
        Instant closeTime,
        double open,
        double high,
        double low,
        double close,
        long volume
) {
    public double range() {
        return high - low;
    }

    public double body() {
        return Math.abs(close - open);
    }

    public double typicalPrice() {
        return (high + low + close) / 3.0;
    }

    public boolean isBullish() {
        return close > open;
    }

    public double bodyPct() {
        double rng = range();
        return rng == 0 ? 0 : body() / rng;
    }

    /** Where the close sits within the candle (0 = at low, 1 = at high). */
    public double closePosition() {
        double rng = range();
        return rng == 0 ? 0.5 : (close - low) / rng;
    }
}
