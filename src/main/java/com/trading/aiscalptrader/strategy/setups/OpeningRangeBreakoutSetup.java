package com.trading.aiscalptrader.strategy.setups;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.Signal;
import com.trading.aiscalptrader.domain.enums.SetupType;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.strategy.SetupVote;
import com.trading.aiscalptrader.strategy.Indicators;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * F-003 / spec 4.1: Opening Range Breakout.
 *  - Build OR high/low from 9:15-9:30 candles
 *  - Check breakout 9:30-10:30 with volume confirmation
 *  - Skip if opening gap > 0.5% (F-023)
 *  - Boost confidence on tight OR range
 */
@Component
@RequiredArgsConstructor
public class OpeningRangeBreakoutSetup implements SetupEvaluator {

    private final AutoScalpProperties props;

    @Override
    public Optional<SetupVote> evaluate(SetupContext ctx) {
        if (ctx.candles1m().size() < 5) return Optional.empty();

        var sp = props.getStrategy();
        Candle current = ctx.candles1m().get(ctx.candles1m().size() - 1);

        ZonedDateTime ts = current.closeTime().atZone(props.getSystem().zoneId());
        LocalTime t = ts.toLocalTime();
        if (t.isBefore(sp.getOrbWindowEnd()) || t.isAfter(sp.getOrbTradeWindowEnd())) {
            return Optional.empty();
        }

        // Find 9:15-9:30 OR range
        double orHigh = Double.NEGATIVE_INFINITY, orLow = Double.POSITIVE_INFINITY;
        double dayOpen = Double.NaN;
        long avgVol = 0; int volCount = 0;

        for (Candle c : ctx.sessionCandles1m()) {
            LocalTime ct = c.closeTime().atZone(props.getSystem().zoneId()).toLocalTime();
            // Include the 9:15→9:16 candle (closeTime > 09:15) up to 9:30 inclusive
            if (!ct.isBefore(sp.getOrbWindowStart()) && !ct.isAfter(sp.getOrbWindowEnd())) {
                if (Double.isNaN(dayOpen)) dayOpen = c.open();
                if (c.high() > orHigh) orHigh = c.high();
                if (c.low() < orLow) orLow = c.low();
            }
            avgVol += c.volume();
            volCount++;
        }
        if (Double.isInfinite(orHigh) || Double.isInfinite(orLow) || volCount == 0) return Optional.empty();
        double avg = avgVol / (double) volCount;

        // Gap-day skip
        if (!Double.isNaN(ctx.prevDayClose()) && !Double.isNaN(dayOpen) && ctx.prevDayClose() > 0) {
            double gap = Math.abs(dayOpen - ctx.prevDayClose()) / ctx.prevDayClose();
            if (gap > props.getRisk().getOpeningGapSkipPct()) return Optional.empty();
        }

        // Breakout check
        boolean volOk = current.volume() > avg * 1.2;
        double[] closes1m = ctx.candles1m().stream().mapToDouble(Candle::close).toArray();
        double ema9  = Indicators.emaLatest(closes1m, sp.getEmaFast());
        double ema21 = Indicators.emaLatest(closes1m, sp.getEmaSlow());
        int st5 = Indicators.supertrend(ctx.candles5m(), sp.getSupertrendPeriod(), sp.getSupertrendMultiplier());

        Signal dir = Signal.HOLD;
        double conf = 0.58;
        String why = "";

        if (current.close() > orHigh && volOk) {
            dir = Signal.BUY_CE;
            if (ema9 > ema21 && st5 > 0) conf = 0.72;
            why = "ORB long: close>" + orHigh + " vol=" + volOk;
        } else if (current.close() < orLow && volOk) {
            dir = Signal.BUY_PE;
            if (ema9 < ema21 && st5 < 0) conf = 0.72;
            why = "ORB short: close<" + orLow + " vol=" + volOk;
        }
        if (dir == Signal.HOLD) return Optional.empty();

        // Tight range bonus
        if (!Double.isNaN(ctx.atr1m()) && ctx.atr1m() > 0 && (orHigh - orLow) < ctx.atr1m() * 1.5) {
            conf = Math.min(0.95, conf + 0.05);
        }

        return Optional.of(new SetupVote(SetupType.ORB, dir, conf, current.closeTime(), ctx.currentBarIndex(), why));
    }
}
