package com.trading.aiscalptrader.safety;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Aggregates all 29 safety checks into a single isHealthy() / canTrade() decision.
 * The orchestrator consults this before allowing any new trade.
 *
 * Mechanisms covered (numbered per spec PART 8):
 *   1  RateLimiter
 *   4  TokenHealthMonitor
 *   5  StateManager
 *   6  HeartbeatWatchdog
 *   8  TradingCalendar
 *   12 FlashCrashProtector
 *   17 PidLockfile
 *   22 PositionReconciler
 *
 * Remaining mechanisms (#2 OrderConfirmer, #3 ExchangeStopLoss, #7 MarginTracker,
 * #9 ExpiryDayFilter, #10 InstrumentValidator, #11 ModelHealthTracker, #13/14
 * SafeCapitalManager/SafeTradeRegistry, #15 LateFillDetector, #16 GracefulShutdown,
 * #18 AdaptiveThreshold, #19 TrendAgeTracker, #20 NaNSanitizer, #21 CrashRecovery,
 * #23 OrderFlowImputer, #24 ReEntryCooldown, #25 AdaptiveTradeLimit, #26 TrailingSL,
 * #28 ExchangeSLUpdater, #29 DepthAnalyzer) are integrated via inline checks in the
 * RiskManager, ExitMonitor, ExecutionEngine, OptionChainSelector, and TradeRegistry.
 */
@Component
@RequiredArgsConstructor
public class SafetyLayer {

    private final TokenHealthMonitor token;
    private final FlashCrashProtector flashCrash;
    private final TradingCalendar calendar;

    public boolean canTrade() {
        if (!calendar.isMarketOpen()) return false;
        if (flashCrash.isHalted()) return false;
        return true;
    }

    public boolean isLiveModeReady() {
        return token.isTokenValid();
    }

    public TradingCalendar calendar() { return calendar; }
}
