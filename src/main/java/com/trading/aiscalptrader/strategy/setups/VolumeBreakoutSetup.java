package com.trading.aiscalptrader.strategy.setups;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.Signal;
import com.trading.aiscalptrader.domain.enums.SetupType;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.strategy.SetupVote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Spec 4.4: tight 10-bar consolidation (<0.4% range) + 2.5× volume spike. */
@Component
@RequiredArgsConstructor
public class VolumeBreakoutSetup implements SetupEvaluator {

    private final AutoScalpProperties props;

    @Override
    public Optional<SetupVote> evaluate(SetupContext ctx) {
        if (ctx.candles1m().size() < 11) return Optional.empty();

        List<Candle> last10 = ctx.candles1m().subList(ctx.candles1m().size() - 11, ctx.candles1m().size() - 1);
        Candle current = ctx.candles1m().get(ctx.candles1m().size() - 1);

        double maxHigh = last10.stream().mapToDouble(Candle::high).max().orElse(0);
        double minLow  = last10.stream().mapToDouble(Candle::low).min().orElse(0);
        double recentRange = maxHigh - minLow;
        if (current.close() == 0) return Optional.empty();
        double rangePct = recentRange / current.close();
        if (rangePct >= 0.004) return Optional.empty();

        long avgVol = 0;
        for (Candle c : last10) avgVol += c.volume();
        double avg = avgVol / (double) last10.size();
        if (current.volume() < avg * 2.5) return Optional.empty();

        Signal dir = current.isBullish() ? Signal.BUY_CE : Signal.BUY_PE;
        return Optional.of(new SetupVote(SetupType.VOLUME_BREAKOUT, dir, 0.60,
                current.closeTime(), ctx.currentBarIndex(), "Vol breakout"));
    }
}
