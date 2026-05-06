package com.trading.aiscalptrader.domain.model;

import com.trading.aiscalptrader.domain.enums.ExitReason;
import com.trading.aiscalptrader.domain.enums.OptionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Active trade lifecycle. Mutable for exit-monitoring updates (peak, trailing SL),
 * guarded by SafeTradeRegistry's RLock.
 */
@Data
@Builder
public class Trade {
    private final String id;
    private final long underlyingToken;
    private final String underlyingSymbol;
    private final OptionContract contract;
    private final OptionType optionType;     // CE or PE
    private final int quantity;              // lots × lot_size
    private final int lots;
    private final double entryPrice;         // option premium at fill
    private final Instant entryTime;
    private final double allocationPct;
    private final Set<String> setupNames;    // setups that fired
    private final double signalConfidence;
    private final double underlyingAtEntry;

    private double initialStopLoss;
    private double takeProfit;
    private double trailActivatePrice;
    private double trailGap;
    private double currentStopLoss;
    private double peakPremium;
    private boolean trailingActive;
    private boolean ivElevated;
    private double impliedVol;
    private String exchangeSlOrderId;

    private boolean closed;
    private Double exitPrice;
    private Instant exitTime;
    private ExitReason exitReason;
    private double pnl;

    /** Atomic ref so exit monitor can update without re-reading the whole trade.
     *  @Builder.Default needed — otherwise Lombok's builder leaves this null.
     */
    @Builder.Default
    private final AtomicReference<Double> latestPremium = new AtomicReference<>(0.0);

    public double currentPnl(double premium) {
        return (premium - entryPrice) * quantity;
    }
}
