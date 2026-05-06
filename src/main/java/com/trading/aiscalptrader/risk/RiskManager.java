package com.trading.aiscalptrader.risk;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.ExitReason;
import com.trading.aiscalptrader.domain.enums.OptionType;
import com.trading.aiscalptrader.domain.strategy.DayPlan;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Authoritative risk gate. Implements F-020 to F-025 from spec.
 * Thread-safe via a ReentrantLock (mirrors Python RLock).
 *
 * The 2-SL halt rule (F-020) is THE MOST IMPORTANT rule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskManager {

    private final AutoScalpProperties props;

    @Getter
    private final DailyState daily = new DailyState();

    private final ReentrantLock lock = new ReentrantLock();
    private DayPlan dayPlan;

    public void newSession(LocalDate today) {
        lock.lock();
        try {
            BigDecimal capital = daily.getCurrentCapital().signum() == 0
                    ? props.getRisk().getCapital()
                    : daily.getCurrentCapital();
            daily.reset(today, capital);
            log.info("New session {} with capital ₹{}", today, capital);
        } finally { lock.unlock(); }
    }

    public void applyDayPlan(DayPlan plan) {
        lock.lock();
        try {
            this.dayPlan = plan;
            log.info("Day plan applied: outlook={} alloc-adj={} risk={} avoid={}",
                    plan.marketOutlook(), plan.allocationAdjustment(),
                    plan.riskLevel(), plan.avoidInstruments());
        } finally { lock.unlock(); }
    }

    /** F-020 — checks if trading is currently halted. */
    public boolean isHalted() {
        lock.lock();
        try { return daily.isHaltedFor2Sl() || daily.isProfitTargetHit(); }
        finally { lock.unlock(); }
    }

    /**
     * Decide whether to take a new trade and at what allocation.
     * Implements 2-SL halt + recovery trade + profit lock + reduced alloc + day plan filters.
     */
    public RiskDecision evaluate(long instrumentToken, OptionType direction, LocalTime now,
                                 String instrumentSymbol) {
        lock.lock();
        try {
            var risk = props.getRisk();

            // 20% daily profit lock (F-021)
            if (daily.isProfitTargetHit()) return RiskDecision.deny("Daily 20% profit target locked");

            // Hard halt — 2-SL rule (F-020) + recovery (F-024)
            if (daily.isHaltedFor2Sl()) {
                if (now.isAfter(risk.getRecoveryTradeTime()) && !daily.isRecoveryTradeUsed()) {
                    daily.setRecoveryTradeUsed(true);
                    log.warn("[2-SL HALT] Recovery trade authorized — 1 trade @ {}%",
                            risk.getRecoveryAllocationPct() * 100);
                    return RiskDecision.allow(risk.getRecoveryAllocationPct(),
                            "Recovery trade after 2-SL halt");
                }
                return RiskDecision.deny("Halted by 2-SL rule (no recovery yet)");
            }

            // Max trades per day (F-025 hard cap)
            int maxTrades = risk.getMaxTradesPerDay();
            if (dayPlan != null && dayPlan.maxTradesOverride() != null) {
                maxTrades = Math.min(maxTrades, dayPlan.maxTradesOverride());
            }
            if (daily.getTradesTaken().get() >= maxTrades) {
                return RiskDecision.deny("Max trades per day reached");
            }

            // Day-plan filters
            if (dayPlan != null) {
                if (dayPlan.avoidInstruments() != null
                        && dayPlan.avoidInstruments().stream()
                                .anyMatch(s -> s.equalsIgnoreCase(instrumentSymbol))) {
                    return RiskDecision.deny("Day plan avoid: " + instrumentSymbol);
                }
                if (!dayPlan.prefersDirection(direction)) {
                    return RiskDecision.deny("Day plan prefers " + dayPlan.preferredDirection());
                }
            }

            // Allocation: 30% default, 20% after first SL (F-022), 15% on event days
            double pct = risk.getCapitalPerTradePct();
            if (daily.getSlHits().get() >= 1) pct = risk.getReducedAllocAfterSl();

            // Day plan adjustment (HIGH risk → 0.7x)
            if (dayPlan != null) {
                if (dayPlan.isHighRisk()) {
                    pct = pct * 0.7;
                } else {
                    pct = pct * Math.max(0.5, Math.min(1.5, dayPlan.allocationAdjustment()));
                }
                // Special events
                if (dayPlan.eventsToday() != null && !dayPlan.eventsToday().isEmpty()) {
                    pct = Math.min(pct, risk.getSpecialEventAllocationPct());
                }
            }

            return RiskDecision.allow(pct, "OK");
        } finally { lock.unlock(); }
    }

    /** Called by ExitMonitor after every closed trade. */
    public void recordExit(ExitReason reason, BigDecimal pnl) {
        lock.lock();
        try {
            daily.recordExit(reason, pnl);
            var risk = props.getRisk();

            // 2-SL halt check
            if (daily.getSlHits().get() >= risk.getMaxSlPerDay() && !daily.isHaltedFor2Sl()) {
                daily.setHaltedFor2Sl(true);
                log.warn("[2-SL HALT] {} stop-losses hit today — trading paused until recovery window @ {}",
                        daily.getSlHits().get(), risk.getRecoveryTradeTime());
            }

            // 20% profit lock check
            if (daily.getStartCapital().signum() > 0) {
                BigDecimal profit = daily.getCurrentCapital().subtract(daily.getStartCapital());
                BigDecimal target = daily.getStartCapital()
                        .multiply(BigDecimal.valueOf(risk.getDailyProfitTargetPct()));
                if (profit.compareTo(target) >= 0 && !daily.isProfitTargetHit()) {
                    daily.setProfitTargetHit(true);
                    log.warn("[PROFIT LOCK] +{}% target reached — closing for the day",
                            risk.getDailyProfitTargetPct() * 100);
                }
            }
        } finally { lock.unlock(); }
    }

    /** Compute trade quantity given premium, lot size, and allocation. */
    public int calculateLots(double premiumPerShare, int lotSize, double allocationPct) {
        BigDecimal cap = daily.getCurrentCapital();
        BigDecimal alloc = cap.multiply(BigDecimal.valueOf(allocationPct));
        double tradeValue = premiumPerShare * lotSize;
        if (tradeValue <= 0) return 0;
        int lots = alloc.divide(BigDecimal.valueOf(tradeValue), 0, RoundingMode.FLOOR).intValue();
        return Math.max(0, lots);
    }
}
