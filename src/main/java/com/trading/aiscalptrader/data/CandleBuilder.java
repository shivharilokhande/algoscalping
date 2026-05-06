package com.trading.aiscalptrader.data;

import com.trading.aiscalptrader.domain.enums.CandleInterval;
import com.trading.aiscalptrader.domain.model.Candle;
import com.trading.aiscalptrader.domain.model.Tick;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Builds OHLCV candles from a tick stream for one (instrument, interval) pair.
 * Caller invokes onTick(tick) sequentially. When a candle closes, returns it.
 *
 * Right-aligned timestamps (closeTime). Volumes are deltas between
 * consecutive cumulative volume_traded fields.
 */
public class CandleBuilder {
    private final long instrumentToken;
    private final CandleInterval interval;
    private final Duration bucketSize;

    private Instant currentBucketStart;
    private Instant currentBucketEnd;
    private double open, high, low, close;
    private long lastVolumeTraded = -1;
    private long bucketVolume = 0;
    private boolean active = false;

    public CandleBuilder(long instrumentToken, CandleInterval interval) {
        this.instrumentToken = instrumentToken;
        this.interval = interval;
        this.bucketSize = interval.duration();
    }

    /** Feed one tick. Returns Optional.of(candle) when a bucket closes. */
    public Optional<Candle> onTick(Tick tick) {
        Optional<Candle> closed = Optional.empty();
        Instant ts = tick.timestamp();
        Instant bucketStart = floor(ts, bucketSize);
        Instant bucketEnd = bucketStart.plus(bucketSize);

        if (!active) {
            startBucket(bucketStart, bucketEnd, tick);
        } else if (!ts.isBefore(currentBucketEnd)) {
            // Previous bucket closed
            closed = Optional.of(emit());
            startBucket(bucketStart, bucketEnd, tick);
        }

        // Update OHLC of current bucket
        double price = tick.lastPrice();
        if (price > high) high = price;
        if (price < low) low = price;
        close = price;

        // Volume delta (Kite gives cumulative day volume)
        if (lastVolumeTraded < 0) {
            lastVolumeTraded = tick.volumeTraded();
        } else if (tick.volumeTraded() > lastVolumeTraded) {
            bucketVolume += (tick.volumeTraded() - lastVolumeTraded);
            lastVolumeTraded = tick.volumeTraded();
        }
        return closed;
    }

    /** Force flush any partial bucket as a candle (e.g., session end). */
    public Optional<Candle> flush() {
        if (!active) return Optional.empty();
        Candle c = emit();
        active = false;
        return Optional.of(c);
    }

    private void startBucket(Instant start, Instant end, Tick tick) {
        currentBucketStart = start;
        currentBucketEnd = end;
        open = high = low = close = tick.lastPrice();
        bucketVolume = 0;
        active = true;
    }

    private Candle emit() {
        Candle c = new Candle(
                instrumentToken,
                interval,
                currentBucketStart,
                currentBucketEnd,
                open, high, low, close,
                bucketVolume
        );
        return c;
    }

    /** Floor an instant to a bucket boundary aligned to epoch. */
    static Instant floor(Instant ts, Duration bucket) {
        long bucketSec = bucket.getSeconds();
        long secs = ts.getEpochSecond();
        long floored = secs - (secs % bucketSec);
        return Instant.ofEpochSecond(floored).truncatedTo(ChronoUnit.SECONDS);
    }

    public CandleInterval interval() { return interval; }
    public long instrumentToken() { return instrumentToken; }
}
