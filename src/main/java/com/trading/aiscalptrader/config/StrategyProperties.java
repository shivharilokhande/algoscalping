package com.trading.aiscalptrader.config;

import lombok.Data;

import java.time.LocalTime;

/** Strategy/indicator parameters mirroring strategy_highwr.py defaults. */
@Data
public class StrategyProperties {
    private int minConfluenceSetups = 2;     // Layer 1: 2 of 4 setups
    private int confluenceWindowBars = 5;    // setups must agree within 5 bars
    private int minLayer2Passed = 3;         // Layer 2: 3 of 4 confirmations
    private int rsiPeriod = 14;
    private int atrPeriod = 14;
    private int emaFast = 9;
    private int emaSlow = 21;
    private int macdFast = 12;
    private int macdSlow = 26;
    private int macdSignal = 9;
    private int supertrendPeriod = 10;
    private double supertrendMultiplier = 3.0;
    private LocalTime orbWindowStart = LocalTime.of(9, 15);
    private LocalTime orbWindowEnd = LocalTime.of(9, 30);
    private LocalTime orbTradeWindowEnd = LocalTime.of(10, 30);
}
