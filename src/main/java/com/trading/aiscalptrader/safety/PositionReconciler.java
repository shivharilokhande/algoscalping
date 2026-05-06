package com.trading.aiscalptrader.safety;

import com.trading.aiscalptrader.domain.enums.TradingMode;
import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.model.Trade;
import com.trading.aiscalptrader.execution.ExecutionEngine;
import com.trading.aiscalptrader.exit.TradeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Safety #22 — every 60s reconcile our local active trades with broker. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionReconciler {

    private final AutoScalpProperties props;
    private final TradeRegistry registry;
    private final ExecutionEngine execution;

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        if (props.getSystem().getMode() != TradingMode.LIVE) return;
        for (Trade t : registry.activeTrades()) {
            int brokerQty = execution.getNetQuantity(t.getContract().tradingSymbol());
            if (brokerQty == 0 && !t.isClosed()) {
                log.warn("[RECONCILE] Local {} active but broker shows zero — closing locally",
                        t.getContract().tradingSymbol());
                t.setClosed(true);
            }
        }
    }
}
