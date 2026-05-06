package com.trading.aiscalptrader.execution;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.data.MarketDataBus;
import com.trading.aiscalptrader.domain.enums.TradingMode;
import com.trading.aiscalptrader.domain.model.Tick;
import com.trading.aiscalptrader.domain.model.Trade;
import com.trading.aiscalptrader.exit.TradeRegistry;
import com.trading.aiscalptrader.greeks.BlackScholes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Paper-mode helper: when the underlying ticks, recompute each open option's
 * theoretical premium via Black-Scholes and feed it back into the
 * PaperExecutionEngine so ExitMonitor sees realistic SL/TP triggers.
 *
 * Without this, paper trades only ever exit at EOD because the recorded
 * fill price is the only price the exit monitor knows about.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "autoscalp.system.mode", havingValue = "PAPER", matchIfMissing = true)
@RequiredArgsConstructor
public class PaperPremiumSimulator {

    private final AutoScalpProperties props;
    private final MarketDataBus bus;
    private final TradeRegistry registry;
    private final PaperExecutionEngine paper;

    @PostConstruct
    public void wire() {
        bus.subscribeTicks(this::onTick);
        log.info("PaperPremiumSimulator wired (paper mode only)");
    }

    private void onTick(Tick tick) {
        if (props.getSystem().getMode() != TradingMode.PAPER) return;
        for (Trade t : registry.activeTrades()) {
            if (t.getUnderlyingToken() != tick.instrumentToken()) continue;
            double premium = simulatePremium(t, tick.lastPrice());
            paper.updateLtp(t.getContract().tradingSymbol(), premium);
        }
    }

    /** Black-Scholes price using current underlying. */
    private double simulatePremium(Trade trade, double underlyingNow) {
        var c = trade.getContract();
        double T = Math.max(0.5 / 365.0,
                ChronoUnit.DAYS.between(LocalDate.now(props.getSystem().zoneId()), c.expiry()) / 365.0);
        double sigma = props.getOptions().getAnnualVolatilityDefault();
        return BlackScholes.price(
                c.optionType().isCall(),
                underlyingNow,
                c.strike(),
                T,
                props.getOptions().getRiskFreeRate(),
                sigma);
    }
}
