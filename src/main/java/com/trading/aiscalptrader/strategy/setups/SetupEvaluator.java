package com.trading.aiscalptrader.strategy.setups;

import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.strategy.SetupVote;

import java.util.List;
import java.util.Optional;

/** Common interface for the 4 setups (ORB, VWAP pullback, EMA crossover, Volume breakout). */
public interface SetupEvaluator {

    /**
     * @return Optional.empty() if no signal this bar, else SetupVote with direction + confidence.
     */
    Optional<SetupVote> evaluate(SetupContext ctx);

    record SetupContext(
            long instrumentToken,
            List<Candle> candles1m,
            List<Candle> candles5m,
            List<Candle> candles15m,
            List<Candle> sessionCandles1m,
            double prevDayClose,
            long currentBarIndex,
            double atr1m
    ) { }
}
