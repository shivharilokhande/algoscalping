package com.trading.aiscalptrader.execution;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.model.OptionContract;
import com.trading.aiscalptrader.risk.RiskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Computes lot count for a trade given allocation %, lot size, and current capital.
 * Mirrors options_sizer.py.
 */
@Service
@RequiredArgsConstructor
public class PositionSizer {

    private final AutoScalpProperties props;
    private final RiskManager riskManager;

    public int sizeLots(OptionContract contract, double allocationPct) {
        double premium = contract.estimatedPremium();
        return riskManager.calculateLots(premium, contract.lotSize(), allocationPct);
    }
}
