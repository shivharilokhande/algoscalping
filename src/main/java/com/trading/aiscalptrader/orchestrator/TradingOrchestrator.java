package com.trading.aiscalptrader.orchestrator;

import com.trading.aiscalptrader.ai.DayPlanApplier;
import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.data.MarketDataBus;
import com.trading.aiscalptrader.domain.enums.CandleInterval;
import com.trading.aiscalptrader.domain.enums.OptionType;
import com.trading.aiscalptrader.domain.enums.Signal;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.model.OptionContract;
import com.trading.aiscalptrader.domain.model.OrderResult;
import com.trading.aiscalptrader.domain.model.Trade;
import com.trading.aiscalptrader.domain.strategy.HighWRResult;
import com.trading.aiscalptrader.execution.ExecutionEngine;
import com.trading.aiscalptrader.execution.InstrumentResolver;
import com.trading.aiscalptrader.execution.OptionChainSelector;
import com.trading.aiscalptrader.execution.PositionSizer;
import com.trading.aiscalptrader.exit.TradeRegistry;
import com.trading.aiscalptrader.greeks.IvDetector;
import com.trading.aiscalptrader.persistence.TradeJournal;
import com.trading.aiscalptrader.risk.RiskDecision;
import com.trading.aiscalptrader.risk.RiskManager;
import com.trading.aiscalptrader.safety.SafetyLayer;
import com.trading.aiscalptrader.strategy.SignalBuffer;
import com.trading.aiscalptrader.strategy.StrategyEngine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The brain. On every closed 1-minute candle:
 *   1. StrategyEngine evaluates the instrument
 *   2. If actionable, buffer it
 *   3. Once all 3 instruments have evaluated this minute, drain & execute the best
 *
 * On every closed candle for instrument N, we also use the wall-clock to decide
 * when "the minute" is over for the cross-instrument best-pick.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingOrchestrator {

    private final AutoScalpProperties props;
    private final MarketDataBus bus;
    private final StrategyEngine strategyEngine;
    private final SignalBuffer signalBuffer;
    private final RiskManager riskManager;
    private final SafetyLayer safety;
    private final TradeRegistry registry;
    private final ExecutionEngine execution;
    private final OptionChainSelector chainSelector;
    private final PositionSizer sizer;
    private final IvDetector ivDetector;
    private final TradeJournal journal;
    private final DayPlanApplier dayPlanApplier;
    /** Present only in LIVE mode — paper mode keeps constructed symbols. */
    private final ObjectProvider<InstrumentResolver> instrumentResolver;

    private final AtomicReference<LocalDate> currentSessionDate = new AtomicReference<>();
    private final Map<Long, Double> lastPrice = new HashMap<>();

    @PostConstruct
    public void wire() {
        bus.subscribeCandles(this::onCandle);
        bus.subscribeTicks(tick -> lastPrice.put(tick.instrumentToken(), tick.lastPrice()));
        log.info("TradingOrchestrator wired up");
    }

    private void onCandle(Candle candle) {
        if (candle.interval() != CandleInterval.ONE_MIN) return;

        rotateDayIfNeeded();

        if (!safety.canTrade()) return;
        if (riskManager.isHalted() && !canRecoveryNow()) return;

        // 1) Evaluate strategy
        HighWRResult res = strategyEngine.evaluateOnNewCandle(candle.instrumentToken(), candle);
        if (!res.isExecutable()) return;

        // 2) Buffer
        signalBuffer.add(new SignalBuffer.BufferedSignal(
                candle.instrumentToken(), res,
                lastPrice.getOrDefault(candle.instrumentToken(), candle.close()),
                candle.closeTime()));
    }

    /** Drain the buffer at every tick after the candle minute completes (every 5s). */
    @Scheduled(fixedDelay = 5000)
    public void drainAndExecute() {
        // Re-check safety here — signal might have been buffered before market closed,
        // or before flash crash / 2-SL halt fired between buffering and draining.
        if (!safety.canTrade()) {
            if (signalBuffer.size() > 0) signalBuffer.clear();
            return;
        }
        if (riskManager.isHalted() && !canRecoveryNow()) {
            signalBuffer.clear();
            return;
        }
        if (registry.hasActive()) return;

        Optional<SignalBuffer.BufferedSignal> bestOpt = signalBuffer.drainBest();
        if (bestOpt.isEmpty()) return;

        SignalBuffer.BufferedSignal best = bestOpt.get();
        OptionType type = best.result().signal().toOptionType();
        String symbol = props.getSystem().getTradingSymbols().getOrDefault(best.instrumentToken(), "");

        ZoneId z = props.getSystem().zoneId();
        RiskDecision decision = riskManager.evaluate(
                best.instrumentToken(), type, LocalTime.now(z), symbol);
        if (!decision.allowed()) {
            log.info("[RISK DENY] {}", decision.reason());
            return;
        }

        // Build option contract — resolve to real Kite instrument in live mode
        LocalDate today = LocalDate.now(z);
        OptionContract contract = chainSelector.select(
                best.instrumentToken(), symbol, best.ltp(), type, today);
        InstrumentResolver resolver = instrumentResolver.getIfAvailable();
        if (resolver != null) {
            var resolved = resolver.resolve(contract);
            if (resolved.isPresent()) {
                contract = resolved.get();
            } else {
                log.warn("[RESOLVE MISS] No NFO instrument matched {} — keeping constructed symbol",
                        contract.tradingSymbol());
            }
        }

        int lots = sizer.sizeLots(contract, decision.allocationPct());
        if (lots <= 0) {
            log.info("[SIZE 0] Capital insufficient for {} @ ₹{}", contract.tradingSymbol(),
                    contract.estimatedPremium());
            return;
        }

        // Place LIMIT BUY — for live, broker returns OPEN/PENDING and we poll for fill
        OrderResult fill = execution.placeBuy(contract, lots, contract.estimatedPremium());
        if (!fill.isAccepted()) {
            log.warn("[ORDER FAIL] {} status={} msg={}",
                    contract.tradingSymbol(), fill.status(), fill.message());
            return;
        }

        double entry = fill.averagePrice() > 0 ? fill.averagePrice() : contract.estimatedPremium();
        double sl = entry * (1.0 - props.getRisk().getOptionSlPct());
        double tpPct = props.getRisk().getOptionTpPct();

        // IV-aware TP tightening — back-solve IV from market premium against BS
        double tte = Math.max(0.5 / 365.0,
                java.time.temporal.ChronoUnit.DAYS.between(today, contract.expiry()) / 365.0);
        double iv = com.trading.aiscalptrader.greeks.BlackScholes.impliedVolatility(
                type.isCall(), entry,
                contract.underlyingPrice(), contract.strike(), tte,
                props.getOptions().getRiskFreeRate());
        if (!Double.isNaN(iv)) ivDetector.update(iv);
        boolean ivElevated = !Double.isNaN(iv)
                && ivDetector.isElevated(iv, props.getRisk().getIvElevatedZscore());
        if (ivElevated) {
            tpPct = props.getRisk().getIvElevatedTpPct();
            log.warn("[IV ELEVATED] iv={} mean={} → tighten TP to {}%",
                    iv, ivDetector.getMean(), tpPct * 100);
        }

        double tp = entry * (1.0 + tpPct);
        double trailActivate = entry * (1.0 + props.getRisk().getTrailActivatePct());

        Trade trade = Trade.builder()
                .id(UUID.randomUUID().toString())
                .underlyingToken(best.instrumentToken())
                .underlyingSymbol(symbol)
                .contract(contract)
                .optionType(type)
                .quantity(lots * contract.lotSize())
                .lots(lots)
                .entryPrice(entry)
                .entryTime(java.time.Instant.now())
                .allocationPct(decision.allocationPct())
                .setupNames(new LinkedHashSet<>(best.result().confluenceSetups()))
                .signalConfidence(best.result().confidence())
                .underlyingAtEntry(best.ltp())
                .initialStopLoss(sl)
                .currentStopLoss(sl)
                .takeProfit(tp)
                .trailActivatePrice(trailActivate)
                .trailGap(entry * props.getRisk().getTrailGapPct())
                .peakPremium(entry)
                .ivElevated(ivElevated)
                .build();

        // Place exchange SL-M (F-011)
        OrderResult slOrder = execution.placeStopLoss(contract, lots, sl);
        trade.setExchangeSlOrderId(slOrder.orderId());

        registry.register(trade);
        journal.recordEntry(trade);
        log.info("[ENTRY {}] {} lots={} entry=₹{} SL={} TP={} setups={} conf={}",
                type, contract.tradingSymbol(), lots, entry, sl, tp,
                trade.getSetupNames(), trade.getSignalConfidence());
    }

    private boolean canRecoveryNow() {
        ZoneId z = props.getSystem().zoneId();
        return LocalTime.now(z).isAfter(props.getRisk().getRecoveryTradeTime())
                && !riskManager.getDaily().isRecoveryTradeUsed();
    }

    private void rotateDayIfNeeded() {
        LocalDate today = LocalDate.now(props.getSystem().zoneId());
        if (!today.equals(currentSessionDate.get())) {
            currentSessionDate.set(today);
            riskManager.newSession(today);
            registry.resetForDay();
            strategyEngine.resetForDay();
            signalBuffer.clear();
            dayPlanApplier.applyForToday(today);
            log.info("=== Session start: {} ===", today);
        }
    }
}
