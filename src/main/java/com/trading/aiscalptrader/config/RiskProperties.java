package com.trading.aiscalptrader.config;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Risk parameters — BACKTEST-PROVEN (do not change without re-validation).
 * 56.9% win rate on 1,402 trades across 553 days of real BankNIFTY data.
 */
@Data
public class RiskProperties {
    private BigDecimal capital = new BigDecimal("50000.00");
    private double capitalPerTradePct = 0.30;          // 30% of capital per trade
    private double optionSlPct = 0.25;                  // 25% premium drop = SL
    private double optionTpPct = 0.30;                  // 30% premium rise = TP
    private double trailActivatePct = 0.45;             // trailing SL activates @ 45% profit
    private double trailGapPct = 0.09;                  // 9% gap from peak
    private int maxTradesPerDay = 5;
    private int maxSlPerDay = 2;                        // 2-SL halt rule (CRITICAL)
    private double dailyProfitTargetPct = 0.20;         // STOP if up 20% today
    private double reducedAllocAfterSl = 0.20;          // 20% after first SL
    private double openingGapSkipPct = 0.005;           // skip ORB if gap > 0.5%
    private int maxConcurrentPositions = 1;
    private double ivElevatedTpPct = 0.20;              // tighten TP when IV elevated
    private double ivElevatedZscore = 1.3;
    private double flashCrashPct = 0.03;                // halt if index moves >3%
    private int atrLowPercentile = 25;                  // skip if ATR in bottom 25th
    private LocalTime recoveryTradeTime = LocalTime.of(13, 30);
    private double recoveryAllocationPct = 0.10;
    private double specialEventAllocationPct = 0.15;
}
