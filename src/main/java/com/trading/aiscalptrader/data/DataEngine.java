package com.trading.aiscalptrader.data;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.CandleInterval;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.model.Tick;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Owns CandleBuilders and CandleSeries for every (instrument, interval).
 * Receives ticks from KiteTickerClient (or PaperTickerClient) and republishes
 * tick + closed candle events on the MarketDataBus.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataEngine {

    private final AutoScalpProperties props;
    private final MarketDataBus bus;

    /** instrumentToken -> interval -> builder */
    private final Map<Long, EnumMap<CandleInterval, CandleBuilder>> builders = new HashMap<>();
    /** instrumentToken -> interval -> rolling series */
    private final Map<Long, EnumMap<CandleInterval, CandleSeries>> seriesMap = new HashMap<>();

    @PostConstruct
    public void initSeries() {
        for (Long token : props.getSystem().getInstruments()) {
            EnumMap<CandleInterval, CandleBuilder> b = new EnumMap<>(CandleInterval.class);
            EnumMap<CandleInterval, CandleSeries> s = new EnumMap<>(CandleInterval.class);
            for (CandleInterval iv : CandleInterval.values()) {
                b.put(iv, new CandleBuilder(token, iv));
                s.put(iv, new CandleSeries(500));
            }
            builders.put(token, b);
            seriesMap.put(token, s);
        }
        log.info("DataEngine initialized for {} instruments × {} intervals",
                props.getSystem().getInstruments().size(), CandleInterval.values().length);
    }

    public void onTick(Tick tick) {
        bus.publishTick(tick);

        EnumMap<CandleInterval, CandleBuilder> b = builders.get(tick.instrumentToken());
        if (b == null) return;

        for (CandleInterval iv : CandleInterval.values()) {
            CandleBuilder builder = b.get(iv);
            builder.onTick(tick).ifPresent(closed -> {
                seriesMap.get(tick.instrumentToken()).get(iv).add(closed);
                bus.publishCandle(closed);
            });
        }
    }

    /** Force flush partial candles (e.g., at EOD or shutdown). */
    public void flush() {
        builders.forEach((token, map) -> map.forEach((iv, builder) ->
                builder.flush().ifPresent(c -> {
                    seriesMap.get(token).get(iv).add(c);
                    bus.publishCandle(c);
                })
        ));
    }

    public CandleSeries series(long instrumentToken, CandleInterval interval) {
        EnumMap<CandleInterval, CandleSeries> map = seriesMap.get(instrumentToken);
        return map == null ? null : map.get(interval);
    }

    /** Seed historical candles on session start so indicators have history. */
    public void seedHistorical(long instrumentToken, CandleInterval interval, java.util.List<Candle> historical) {
        CandleSeries s = series(instrumentToken, interval);
        if (s == null) return;
        historical.forEach(s::add);
        log.info("Seeded {} historical {} candles for {}", historical.size(), interval, instrumentToken);
    }
}
