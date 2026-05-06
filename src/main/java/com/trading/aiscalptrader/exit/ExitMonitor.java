package com.trading.aiscalptrader.exit;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.ExitReason;
import com.trading.aiscalptrader.domain.enums.OptionType;
import com.trading.aiscalptrader.domain.model.OrderResult;
import com.trading.aiscalptrader.domain.model.Trade;
import com.trading.aiscalptrader.execution.ExecutionEngine;
import com.trading.aiscalptrader.persistence.TradeJournal;
import com.trading.aiscalptrader.risk.RiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Spec PART 5 — exit management.
 *  - 25% premium drop → STOP_LOSS
 *  - 30% premium rise → TAKE_PROFIT (20% if IV elevated)
 *  - 45% profit → activate trailing, 9% gap from peak (only moves up)
 *  - 15:25 → EOD auto-close (F-019)
 *
 * Polled every 1s. For live mode, broker SL-M is the hardware stop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExitMonitor {

    private final AutoScalpProperties props;
    private final TradeRegistry registry;
    private final ExecutionEngine execution;
    private final RiskManager riskManager;
    private final TradeJournal journal;

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        for (Trade trade : registry.activeTrades()) {
            try { tickTrade(trade); }
            catch (Exception e) { log.error("ExitMonitor failed for {}: {}", trade.getId(), e.getMessage(), e); }
        }
        eodIfDue();
    }

    void tickTrade(Trade trade) {
        if (trade.isClosed()) return;

        double premium = execution.getLtp(trade.getContract().tradingSymbol());
        if (premium <= 0) return;
        trade.getLatestPremium().set(premium);

        // Update peak
        if (premium > trade.getPeakPremium()) trade.setPeakPremium(premium);

        // Trailing SL activation & gap
        var risk = props.getRisk();
        double activateLevel = trade.getEntryPrice() * (1.0 + risk.getTrailActivatePct());
        if (!trade.isTrailingActive() && trade.getPeakPremium() >= activateLevel) {
            trade.setTrailingActive(true);
            double newSl = trade.getPeakPremium() * (1.0 - risk.getTrailGapPct());
            trade.setCurrentStopLoss(newSl);
            log.info("[TRAIL ON] {} entry={} peak={} → trailSL={}",
                    trade.getContract().tradingSymbol(),
                    trade.getEntryPrice(), trade.getPeakPremium(), newSl);
            if (trade.getExchangeSlOrderId() != null) {
                execution.modifyStopLoss(trade.getExchangeSlOrderId(), newSl);
            }
        }

        // Trail SL only moves up
        if (trade.isTrailingActive()) {
            double candidate = trade.getPeakPremium() * (1.0 - risk.getTrailGapPct());
            if (candidate > trade.getCurrentStopLoss()) {
                trade.setCurrentStopLoss(candidate);
                if (trade.getExchangeSlOrderId() != null) {
                    execution.modifyStopLoss(trade.getExchangeSlOrderId(), candidate);
                }
            }
        }

        // Exit checks
        boolean isCe = trade.getOptionType() == OptionType.CE;
        double sl = trade.getCurrentStopLoss();
        double tp = trade.getTakeProfit();

        // Premium-based: long option ⇒ exit on premium drop or rise
        if (premium <= sl) {
            ExitReason reason = trade.isTrailingActive() ? ExitReason.TRAILING_SL : ExitReason.STOP_LOSS;
            closeTrade(trade, premium, reason);
        } else if (!trade.isTrailingActive() && premium >= tp) {
            closeTrade(trade, premium, ExitReason.TAKE_PROFIT);
        }
    }

    private void eodIfDue() {
        ZoneId z = props.getSystem().zoneId();
        LocalTime now = LocalTime.now(z);
        if (!now.isAfter(props.getSystem().getEodCloseTime())) return;
        for (Trade t : registry.activeTrades()) {
            double premium = execution.getLtp(t.getContract().tradingSymbol());
            closeTrade(t, premium > 0 ? premium : t.getEntryPrice(), ExitReason.EOD_CLOSE);
        }
    }

    public synchronized void closeTrade(Trade trade, double exitPremium, ExitReason reason) {
        if (trade.isClosed()) return;
        OrderResult sell = execution.placeSell(trade.getContract(), trade.getLots());
        if (trade.getExchangeSlOrderId() != null) {
            execution.cancel(trade.getExchangeSlOrderId());
        }
        trade.setClosed(true);
        trade.setExitPrice(exitPremium);
        trade.setExitTime(Instant.now());
        trade.setExitReason(reason);
        BigDecimal pnl = BigDecimal.valueOf((exitPremium - trade.getEntryPrice()) * trade.getQuantity());
        trade.setPnl(pnl.doubleValue());
        registry.close(trade);
        riskManager.recordExit(reason, pnl);
        journal.recordExit(trade);
        log.info("[EXIT {}] {} entry={} exit={} pnl=₹{} reason={}",
                reason, trade.getContract().tradingSymbol(),
                trade.getEntryPrice(), exitPremium, pnl, reason);
    }

    /** Fire from RiskManager when 2-SL halt or external shutdown forces close. */
    public void forceCloseAll(ExitReason reason) {
        for (Trade t : registry.activeTrades()) {
            double premium = execution.getLtp(t.getContract().tradingSymbol());
            closeTrade(t, premium > 0 ? premium : t.getEntryPrice(), reason);
        }
    }
}
