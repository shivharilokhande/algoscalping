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

/** Spec 4.3: 9 EMA crosses over/under 21 EMA in last 3 bars, 5m supertrend aligned. */
@Component
@RequiredArgsConstructor
public class EmaCrossoverSetup implements SetupEvaluator {

    private final AutoScalpProperties props;

    @Override
    public Optional<SetupVote> evaluate(SetupContext ctx) {
        var sp = props.getStrategy();
        if (ctx.candles1m().size() < sp.getEmaSlow() + 5) return Optional.empty();

        double[] closes = ctx.candles1m().stream().mapToDouble(Candle::close).toArray();
        double[] ema9  = Indicators.ema(closes, sp.getEmaFast());
        double[] ema21 = Indicators.ema(closes, sp.getEmaSlow());

        int n = closes.length;
        if (n < 5) return Optional.empty();
        double prevFast = ema9[n - 4],  prevSlow = ema21[n - 4];
        double currFast = ema9[n - 1],  currSlow = ema21[n - 1];
        if (Double.isNaN(prevFast) || Double.isNaN(prevSlow)) return Optional.empty();

        int st5 = Indicators.supertrend(ctx.candles5m(), sp.getSupertrendPeriod(), sp.getSupertrendMultiplier());
        Candle current = ctx.candles1m().get(n - 1);

        Signal dir = Signal.HOLD;
        if (prevFast < prevSlow && currFast > currSlow && st5 > 0) {
            dir = Signal.BUY_CE;
        } else if (prevFast > prevSlow && currFast < currSlow && st5 < 0) {
            dir = Signal.BUY_PE;
        }
        if (dir == Signal.HOLD) return Optional.empty();

        double conf = 0.52;
        // Boost if price aligned with VWAP
        double vwap = Indicators.vwap(ctx.sessionCandles1m());
        if (!Double.isNaN(vwap)) {
            if (dir == Signal.BUY_CE && current.close() > vwap) conf += 0.06;
            if (dir == Signal.BUY_PE && current.close() < vwap) conf += 0.06;
        }
        return Optional.of(new SetupVote(SetupType.EMA_CROSSOVER, dir, Math.min(0.95, conf),
                current.closeTime(), ctx.currentBarIndex(), "EMA9/21 cross"));
    }
}
