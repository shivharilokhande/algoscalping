package com.trading.aiscalptrader.execution;

import com.trading.aiscalptrader.domain.model.OptionContract;
import com.trading.aiscalptrader.domain.model.OrderResult;

/**
 * Common interface for paper and live order placement.
 * Implementations: PaperExecutionEngine, LiveExecutionEngine.
 */
public interface ExecutionEngine {

    /** Place a LIMIT BUY at LTP+0.5% (F-009). */
    OrderResult placeBuy(OptionContract contract, int lots, double ltp);

    /** Place exchange-side SL-M (F-011). */
    OrderResult placeStopLoss(OptionContract contract, int lots, double triggerPrice);

    /** Cancel an existing order. */
    boolean cancel(String orderId);

    /** Modify the trigger price of an SL-M (used when trailing SL moves). */
    boolean modifyStopLoss(String orderId, double newTriggerPrice);

    /** Place a market sell to exit a position. */
    OrderResult placeSell(OptionContract contract, int lots);

    /** Get current quote (used by exit monitor for premium tracking). */
    double getLtp(String tradingSymbol);

    /** Reconciliation: list our open positions according to broker. */
    int getNetQuantity(String tradingSymbol);
}
