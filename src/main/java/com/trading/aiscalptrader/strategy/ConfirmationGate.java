package com.trading.aiscalptrader.strategy;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.Signal;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.strategy.Layer2Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Layer 2 — 4 confirmations:
 *   2A: 15-min Supertrend aligned
 *   2B: MACD histogram > 0 & accelerating (or < 0 accelerating for bearish)
 *   2C: relative volume > 1.0 vs time-of-day average
 *   2D: 2 of last 3 candles show directional control (body & close-position)
 * Need 3-of-4 to PASS.
 */
@Component
@RequiredArgsConstructor
public class ConfirmationGate {

    private final AutoScalpProperties props;
    /** time-of-day rolling avg volume per (instrument, minuteOfDay) */
    private final Map<Long, Map<Integer, double[]>> todAvg = new HashMap<>();

    public Layer2Status evaluate(Signal direction,
                                 long instrumentToken,
                                 List<Candle> candles1m,
                                 List<Candle> candles5m,
                                 List<Candle> candles15m) {
        boolean bullish = direction == Signal.BUY_CE;
        var sp = props.getStrategy();

        // 2A — 15m Supertrend
        int st15 = Indicators.supertrend(candles15m, sp.getSupertrendPeriod(), sp.getSupertrendMultiplier());
        boolean l2a = bullish ? st15 > 0 : st15 < 0;

        // 2B — MACD on 5m, current vs prev histogram
        double[] closes5m = candles5m.stream().mapToDouble(Candle::close).toArray();
        double[] hist = Indicators.macdHistTail(closes5m, sp.getMacdFast(), sp.getMacdSlow(), sp.getMacdSignal());
        boolean l2b;
        if (Double.isNaN(hist[0]) || Double.isNaN(hist[1])) l2b = false;
        else if (bullish) l2b = hist[0] > 0 && hist[0] > hist[1];
        else              l2b = hist[0] < 0 && hist[0] < hist[1];

        // 2C — relative volume vs time-of-day rolling mean
        Candle current = candles1m.get(candles1m.size() - 1);
        int minuteOfDay = current.closeTime().atZone(props.getSystem().zoneId())
                .toLocalTime().toSecondOfDay() / 60;
        double[] tod = todAvg.computeIfAbsent(instrumentToken, k -> new HashMap<>())
                .computeIfAbsent(minuteOfDay, k -> new double[]{0, 0});
        double mean = tod[1] == 0 ? current.volume() : tod[0];
        // Exponential moving average update
        double alpha = 0.1;
        tod[0] = tod[1] == 0 ? current.volume() : (1 - alpha) * tod[0] + alpha * current.volume();
        tod[1] = tod[1] + 1;
        boolean l2c = current.volume() > mean;

        // 2D — candle structure (last 3)
        int control = 0;
        int from = Math.max(0, candles1m.size() - 3);
        for (int i = from; i < candles1m.size(); i++) {
            Candle c = candles1m.get(i);
            double bodyPct = c.bodyPct();
            double pos = c.closePosition();
            if (bullish) {
                if (pos > 0.6 && bodyPct > 0.4) control++;
            } else {
                if (pos < 0.4 && bodyPct > 0.4) control++;
            }
        }
        boolean l2d = control >= 2;

        return new Layer2Status(l2a, l2b, l2c, l2d);
    }

    public boolean passes(Layer2Status s) {
        return s.score() >= props.getStrategy().getMinLayer2Passed();
    }
}
