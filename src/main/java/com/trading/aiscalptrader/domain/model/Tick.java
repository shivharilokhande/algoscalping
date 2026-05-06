package com.trading.aiscalptrader.domain.model;

import java.time.Instant;

/** Raw tick from Kite WebSocket. Immutable. */
public record Tick(
        long instrumentToken,
        double lastPrice,
        long volumeTraded,
        double bestBid,
        double bestAsk,
        long bidQty,
        long askQty,
        Instant timestamp
) {
    public double midPrice() {
        return (bestBid > 0 && bestAsk > 0) ? (bestBid + bestAsk) / 2.0 : lastPrice;
    }

    public double spread() {
        return (bestBid > 0 && bestAsk > 0) ? (bestAsk - bestBid) : 0.0;
    }
}
