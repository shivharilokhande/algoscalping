package com.trading.aiscalptrader.execution;

import com.trading.aiscalptrader.domain.model.OptionContract;
import com.trading.aiscalptrader.domain.model.OrderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated execution. F-012 — paper mode is the default.
 * Fills are instant, at LTP+0.5% (matches LIMIT slippage assumption).
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "autoscalp.system.mode", havingValue = "PAPER", matchIfMissing = true)
public class PaperExecutionEngine implements ExecutionEngine {

    /** Tracks simulated positions and stop orders */
    private final ConcurrentHashMap<String, Integer> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> latestPremium = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> stopOrders = new ConcurrentHashMap<>();

    @Override
    public OrderResult placeBuy(OptionContract contract, int lots, double ltp) {
        int qty = lots * contract.lotSize();
        double fillPrice = ltp * 1.005;
        positions.merge(contract.tradingSymbol(), qty, Integer::sum);
        latestPremium.put(contract.tradingSymbol(), fillPrice);
        log.info("[PAPER BUY] {} {} lots × {} @ ₹{} (limit {})",
                contract.tradingSymbol(), lots, contract.lotSize(),
                String.format("%.2f", fillPrice), String.format("%.2f", ltp));
        return OrderResult.builder()
                .orderId(UUID.randomUUID().toString())
                .tradingSymbol(contract.tradingSymbol())
                .status("COMPLETE")
                .quantity(qty)
                .averagePrice(fillPrice)
                .timestamp(Instant.now())
                .message("Paper fill")
                .build();
    }

    @Override
    public OrderResult placeStopLoss(OptionContract contract, int lots, double triggerPrice) {
        String id = UUID.randomUUID().toString();
        stopOrders.put(id, triggerPrice);
        log.info("[PAPER SL-M] {} trigger ₹{}", contract.tradingSymbol(),
                String.format("%.2f", triggerPrice));
        return OrderResult.builder()
                .orderId(id)
                .tradingSymbol(contract.tradingSymbol())
                .status("TRIGGER PENDING")
                .quantity(lots * contract.lotSize())
                .averagePrice(triggerPrice)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    public boolean cancel(String orderId) {
        return stopOrders.remove(orderId) != null;
    }

    @Override
    public boolean modifyStopLoss(String orderId, double newTriggerPrice) {
        if (!stopOrders.containsKey(orderId)) return false;
        stopOrders.put(orderId, newTriggerPrice);
        return true;
    }

    @Override
    public OrderResult placeSell(OptionContract contract, int lots) {
        int qty = lots * contract.lotSize();
        positions.merge(contract.tradingSymbol(), -qty, Integer::sum);
        double fill = latestPremium.getOrDefault(contract.tradingSymbol(), contract.estimatedPremium());
        log.info("[PAPER SELL] {} qty={} @ ₹{}", contract.tradingSymbol(), qty,
                String.format("%.2f", fill));
        return OrderResult.builder()
                .orderId(UUID.randomUUID().toString())
                .tradingSymbol(contract.tradingSymbol())
                .status("COMPLETE")
                .quantity(qty)
                .averagePrice(fill)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    public double getLtp(String tradingSymbol) {
        return latestPremium.getOrDefault(tradingSymbol, 0.0);
    }

    /** Test hook — let exit monitor / paper feed update simulated premiums. */
    public void updateLtp(String tradingSymbol, double premium) {
        latestPremium.put(tradingSymbol, premium);
    }

    @Override
    public int getNetQuantity(String tradingSymbol) {
        return positions.getOrDefault(tradingSymbol, 0);
    }
}
