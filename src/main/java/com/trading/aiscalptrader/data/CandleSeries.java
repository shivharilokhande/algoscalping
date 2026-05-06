package com.trading.aiscalptrader.data;

import com.trading.aiscalptrader.domain.model.Candle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Bounded ring of recent candles for one (instrument, interval).
 * Indicators read this. Thread-safe through synchronized access.
 */
public class CandleSeries {
    private final int capacity;
    private final Deque<Candle> candles;

    public CandleSeries(int capacity) {
        this.capacity = capacity;
        this.candles = new ArrayDeque<>(capacity);
    }

    public synchronized void add(Candle c) {
        if (candles.size() == capacity) candles.pollFirst();
        candles.offerLast(c);
    }

    public synchronized List<Candle> snapshot() {
        return new ArrayList<>(candles);
    }

    public synchronized int size() { return candles.size(); }

    public synchronized Candle last() {
        return candles.isEmpty() ? null : candles.peekLast();
    }

    public synchronized Candle at(int idxFromEnd) {
        if (idxFromEnd >= candles.size()) return null;
        Candle[] arr = candles.toArray(new Candle[0]);
        return arr[arr.length - 1 - idxFromEnd];
    }

    public synchronized double[] closes() {
        return candles.stream().mapToDouble(Candle::close).toArray();
    }

    public synchronized double[] highs() {
        return candles.stream().mapToDouble(Candle::high).toArray();
    }

    public synchronized double[] lows() {
        return candles.stream().mapToDouble(Candle::low).toArray();
    }

    public synchronized double[] opens() {
        return candles.stream().mapToDouble(Candle::open).toArray();
    }

    public synchronized double[] volumes() {
        return candles.stream().mapToDouble(c -> (double) c.volume()).toArray();
    }

    public synchronized double[] typicalPrices() {
        return candles.stream().mapToDouble(Candle::typicalPrice).toArray();
    }

    public synchronized List<Candle> tail(int n) {
        if (n >= candles.size()) return new ArrayList<>(candles);
        List<Candle> all = new ArrayList<>(candles);
        return all.subList(all.size() - n, all.size());
    }
}
