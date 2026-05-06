package com.trading.aiscalptrader.domain.strategy;

/** 4 confirmation indicators (Layer 2). Need 3-of-4 to pass. */
public record Layer2Status(
        boolean supertrend15m,    // 2A: 15-min Supertrend aligned
        boolean macdAccelerating, // 2B: MACD histogram pushing direction
        boolean relativeVolume,   // 2C: volume above time-of-day average
        boolean candleStructure   // 2D: 2-of-3 candles show directional control
) {
    public int score() {
        int s = 0;
        if (supertrend15m) s++;
        if (macdAccelerating) s++;
        if (relativeVolume) s++;
        if (candleStructure) s++;
        return s;
    }
}
