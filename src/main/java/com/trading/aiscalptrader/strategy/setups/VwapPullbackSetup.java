package com.trading.aiscalptrader.strategy.setups;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.Signal;
import com.trading.aiscalptrader.domain.enums.SetupType;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.strategy.SetupVote;
import com.trading.aiscalptrader.strategy.Indicators;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Spec 4.2: VWAP pullback + bounce, trend-aligned. */
@Component
@RequiredArgsConstructor
public class VwapPullbackSetup implements SetupEvaluator {

    private final AutoScalpProperties props;

    @Override
    public Optional<SetupVote> evaluate(SetupContext ctx) {
        var sp = props.getStrategy();
        List<Candle> session = ctx.sessionCandles1m();
        if (session.size() < 5 || ctx.candles1m().size() < sp.getEmaSlow() + 1) return Optional.empty();

        double vwap = Indicators.vwap(session);
        if (Double.isNaN(vwap)) return Optional.empty();

        Candle current = ctx.candles1m().get(ctx.candles1m().size() - 1);
        List<Candle> last3 = ctx.candles1m().subList(Math.max(0, ctx.candles1m().size() - 3), ctx.candles1m().size());
        double minLow3  = last3.stream().mapToDouble(Candle::low).min().orElse(Double.MAX_VALUE);
        double maxHigh3 = last3.stream().mapToDouble(Candle::high).max().orElse(Double.MIN_VALUE);

        double[] closes = ctx.candles1m().stream().mapToDouble(Candle::close).toArray();
        double ema9  = Indicators.emaLatest(closes, sp.getEmaFast());
        double ema21 = Indicators.emaLatest(closes, sp.getEmaSlow());
        int st5 = Indicators.supertrend(ctx.candles5m(), sp.getSupertrendPeriod(), sp.getSupertrendMultiplier());
        double rsi = Indicators.rsi(closes, sp.getRsiPeriod());

        long avgVol = 0; int n = 0;
        for (Candle c : ctx.candles1m()) { avgVol += c.volume(); n++; }
        double avg = n > 0 ? (avgVol / (double) n) : 0;

        Signal dir = Signal.HOLD;
        double conf = 0.55;
        String why = "";

        // Bullish: above VWAP, touched VWAP, bounced, EMAs aligned, ST bullish
        if (current.close() > vwap
                && minLow3 <= vwap * 1.001
                && ema9 > ema21
                && st5 > 0) {
            dir = Signal.BUY_CE;
            why = "VWAP bull pullback @ " + String.format("%.2f", vwap);
        }
        // Bearish: below VWAP, touched, rejected
        else if (current.close() < vwap
                && maxHigh3 >= vwap * 0.999
                && ema9 < ema21
                && st5 < 0) {
            dir = Signal.BUY_PE;
            why = "VWAP bear pullback @ " + String.format("%.2f", vwap);
        }
        if (dir == Signal.HOLD) return Optional.empty();

        if (current.volume() > avg * 1.3) conf += 0.08;
        if (rsi > 40 && rsi < 60) conf += 0.05;
        conf = Math.min(0.95, conf);

        return Optional.of(new SetupVote(SetupType.VWAP_PULLBACK, dir, conf,
                current.closeTime(), ctx.currentBarIndex(), why));
    }
}
