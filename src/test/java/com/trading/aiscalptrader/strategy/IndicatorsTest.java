package com.trading.aiscalptrader.strategy;

import com.trading.aiscalptrader.domain.enums.CandleInterval;
import com.trading.aiscalptrader.domain.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IndicatorsTest {

    @Test
    void emaTracksTrend() {
        double[] series = {10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        double[] ema = Indicators.ema(series, 3);
        // EMA should rise monotonically given monotonic input
        assertThat(ema[ema.length - 1]).isGreaterThan(ema[ema.length - 2]);
        // 50bp tolerance — close to last close
        assertThat(ema[ema.length - 1]).isCloseTo(18.5, within(1.0));
    }

    @Test
    void rsiAtBoundaryConditions() {
        // All up moves → RSI ≈ 100
        double[] up = new double[20];
        for (int i = 0; i < up.length; i++) up[i] = 100 + i;
        assertThat(Indicators.rsi(up, 14)).isCloseTo(100.0, within(0.1));
    }

    @Test
    void atrComputesNonZeroForVolatileSeries() {
        List<Candle> candles = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T09:15:00Z");
        for (int i = 0; i < 30; i++) {
            double close = 100 + i;
            candles.add(new Candle(1L, CandleInterval.ONE_MIN,
                    base.plusSeconds(i * 60L), base.plusSeconds((i + 1) * 60L),
                    close - 0.5, close + 1, close - 1, close, 1000));
        }
        double atr = Indicators.atr(candles, 14);
        assertThat(atr).isGreaterThan(0).isLessThan(5);
    }
}
