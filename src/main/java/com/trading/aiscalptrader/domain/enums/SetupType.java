package com.trading.aiscalptrader.domain.enums;

public enum SetupType {
    ORB,                // Opening Range Breakout
    VWAP_PULLBACK,      // VWAP touch + bounce
    EMA_CROSSOVER,      // 9 over 21 EMA
    VOLUME_BREAKOUT     // tight consolidation + volume spike
}
