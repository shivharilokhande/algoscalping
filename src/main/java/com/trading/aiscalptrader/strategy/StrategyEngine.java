package com.trading.aiscalptrader.strategy;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.data.CandleSeries;
import com.trading.aiscalptrader.data.DataEngine;
import com.trading.aiscalptrader.domain.enums.CandleInterval;
import com.trading.aiscalptrader.domain.enums.Signal;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.strategy.HighWRResult;
import com.trading.aiscalptrader.domain.strategy.Layer2Status;
import com.trading.aiscalptrader.domain.strategy.SetupVote;
import com.trading.aiscalptrader.strategy.setups.SetupEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-instrument strategy evaluation. Runs on every closed 1-minute candle.
 *  1. Run all 4 setup evaluators
 *  2. Record any votes into the rolling window (Layer 1)
 *  3. If confluence detected, run Layer 2
 *  4. Volatility regime gate (skip if ATR in bottom 25%)
 *  5. Return HighWRResult
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyEngine {

    private final AutoScalpProperties props;
    private final DataEngine dataEngine;
    private final List<SetupEvaluator> setups;
    private final ConfluenceGate confluenceGate;
    private final ConfirmationGate confirmationGate;

    private final Map<Long, AtomicLong> barIndex = new HashMap<>();
    private final Map<Long, Double> prevDayClose = new HashMap<>();
    private final Map<Long, LocalDate> sessionDate = new HashMap<>();

    public HighWRResult evaluateOnNewCandle(long instrumentToken, Candle candle1m) {
        long bar = barIndex.computeIfAbsent(instrumentToken, k -> new AtomicLong()).incrementAndGet();

        CandleSeries c1m = dataEngine.series(instrumentToken, CandleInterval.ONE_MIN);
        CandleSeries c5m = dataEngine.series(instrumentToken, CandleInterval.FIVE_MIN);
        CandleSeries c15 = dataEngine.series(instrumentToken, CandleInterval.FIFTEEN_MIN);
        if (c1m == null) return HighWRResult.hold("no series");
        if (c1m.size() < props.getStrategy().getEmaSlow() + 5) return HighWRResult.hold("warming up");

        List<Candle> all1m = c1m.snapshot();
        List<Candle> all5m = c5m == null ? List.of() : c5m.snapshot();
        List<Candle> all15 = c15 == null ? List.of() : c15.snapshot();

        // Per-day session candles for VWAP / ORB
        ZoneId zone = props.getSystem().zoneId();
        LocalDate today = candle1m.closeTime().atZone(zone).toLocalDate();
        if (!Objects.equals(sessionDate.get(instrumentToken), today)) {
            sessionDate.put(instrumentToken, today);
            // capture last close as prev-day close
            int idx = all1m.indexOf(candle1m);
            if (idx > 0) prevDayClose.put(instrumentToken, all1m.get(idx - 1).close());
        }
        List<Candle> session = all1m.stream()
                .filter(c -> c.closeTime().atZone(zone).toLocalDate().equals(today))
                .toList();

        double atr = Indicators.atr(all1m, props.getStrategy().getAtrPeriod());

        // Volatility regime filter — skip when ATR in bottom 25th percentile
        if (volatilityTooLow(all1m, atr)) {
            return HighWRResult.hold("ATR low regime");
        }

        SetupEvaluator.SetupContext ctx = new SetupEvaluator.SetupContext(
                instrumentToken, all1m, all5m, all15, session,
                prevDayClose.getOrDefault(instrumentToken, Double.NaN),
                bar, atr);

        List<SetupVote> votesThisBar = new ArrayList<>();
        for (SetupEvaluator s : setups) {
            try {
                s.evaluate(ctx).ifPresent(v -> {
                    confluenceGate.record(instrumentToken, v, bar);
                    votesThisBar.add(v);
                });
            } catch (Exception e) {
                log.warn("Setup {} failed: {}", s.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Layer 1
        ConfluenceGate.Result l1 = confluenceGate.evaluate(instrumentToken, bar);
        if (!l1.passed()) {
            return HighWRResult.builder()
                    .signal(Signal.HOLD).confidence(0.0)
                    .confluenceSetups(Set.of())
                    .recentVotes(votesThisBar)
                    .layer1Passed(false).layer2Passed(false)
                    .layer2Score(0)
                    .layer2Status(new Layer2Status(false, false, false, false))
                    .reason("L1 fail")
                    .build();
        }

        // Layer 2
        Layer2Status l2 = confirmationGate.evaluate(l1.direction(), instrumentToken, all1m, all5m, all15);
        boolean l2pass = confirmationGate.passes(l2);

        return HighWRResult.builder()
                .signal(l2pass ? l1.direction() : Signal.HOLD)
                .confidence(l2pass ? l1.maxConfidence() : 0.0)
                .confluenceSetups(l1.confluentSetups().stream().map(Enum::name)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))
                .recentVotes(votesThisBar)
                .layer1Passed(true)
                .layer2Passed(l2pass)
                .layer2Score(l2.score())
                .layer2Status(l2)
                .reason(l2pass ? "PASS" : "L2 only " + l2.score() + "/4")
                .build();
    }

    private boolean volatilityTooLow(List<Candle> candles, double currentAtr) {
        double[] series = Indicators.atrSeries(candles, props.getStrategy().getAtrPeriod());
        if (series.length < 50) return false;
        double[] valid = Arrays.stream(series).filter(d -> d > 0).toArray();
        if (valid.length < 20) return false;
        double[] sorted = valid.clone();
        Arrays.sort(sorted);
        int p = (int) Math.floor(sorted.length * (props.getRisk().getAtrLowPercentile() / 100.0));
        double cutoff = sorted[Math.max(0, p)];
        return currentAtr < cutoff;
    }

    public void resetForDay() {
        confluenceGate.resetForDay();
        barIndex.clear();
    }
}
