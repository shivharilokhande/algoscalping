package com.trading.aiscalptrader.api;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.exit.TradeRegistry;
import com.trading.aiscalptrader.risk.RiskManager;
import com.trading.aiscalptrader.safety.SafetyLayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;

/** Renders the Thymeleaf dashboard at "/". */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final AutoScalpProperties props;
    private final RiskManager riskManager;
    private final SafetyLayer safety;
    private final TradeRegistry registry;

    @GetMapping("/")
    public String dashboard(Model model) {
        var d = riskManager.getDaily();
        BigDecimal pnl = d.getCurrentCapital().subtract(d.getStartCapital());
        model.addAttribute("mode", props.getSystem().getMode());
        model.addAttribute("marketOpen", safety.calendar().isMarketOpen());
        model.addAttribute("halted", riskManager.isHalted());
        model.addAttribute("capital", d.getCurrentCapital());
        model.addAttribute("pnl", pnl);
        model.addAttribute("trades", d.getTradesTaken().get());
        model.addAttribute("slHits", d.getSlHits().get());
        model.addAttribute("slLimit", props.getRisk().getMaxSlPerDay());
        model.addAttribute("winners", d.getWinners().get());
        model.addAttribute("profitLock", d.isProfitTargetHit());
        model.addAttribute("recoveryUsed", d.isRecoveryTradeUsed());
        model.addAttribute("activeTrades", registry.activeTrades());
        model.addAttribute("closedTrades", registry.closedTradesToday());
        model.addAttribute("risk", props.getRisk());
        model.addAttribute("strategy", props.getStrategy());
        return "dashboard";
    }
}
