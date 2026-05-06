package com.trading.aiscalptrader.api;

import com.trading.aiscalptrader.api.dto.StatusDto;
import com.trading.aiscalptrader.api.dto.TradeDto;
import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.model.Trade;
import com.trading.aiscalptrader.exit.TradeRegistry;
import com.trading.aiscalptrader.risk.DailyState;
import com.trading.aiscalptrader.risk.RiskManager;
import com.trading.aiscalptrader.safety.SafetyLayer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StatusController {

    private final AutoScalpProperties props;
    private final RiskManager riskManager;
    private final SafetyLayer safety;
    private final TradeRegistry registry;

    @GetMapping("/status")
    public StatusDto status() {
        DailyState d = riskManager.getDaily();
        BigDecimal pnl = d.getCurrentCapital().subtract(d.getStartCapital());
        return StatusDto.builder()
                .running(true)
                .mode(props.getSystem().getMode().name())
                .marketOpen(safety.calendar().isMarketOpen())
                .halted(riskManager.isHalted())
                .sessionDate(d.getDate())
                .capital(d.getCurrentCapital())
                .pnl(pnl)
                .trades(d.getTradesTaken().get())
                .slHits(d.getSlHits().get())
                .slLimit(props.getRisk().getMaxSlPerDay())
                .winners(d.getWinners().get())
                .profitTargetHit(d.isProfitTargetHit())
                .recoveryUsed(d.isRecoveryTradeUsed())
                .activePositions(registry.activeTrades().size())
                .build();
    }

    @GetMapping("/trades/active")
    public List<TradeDto> active() {
        return registry.activeTrades().stream().map(TradeDto::of).toList();
    }

    @GetMapping("/trades/closed")
    public List<TradeDto> closed() {
        return registry.closedTradesToday().stream().map(TradeDto::of).toList();
    }

    @GetMapping("/health")
    public java.util.Map<String, Object> health() {
        return java.util.Map.of(
                "marketOpen", safety.calendar().isMarketOpen(),
                "canTrade", safety.canTrade(),
                "halted", riskManager.isHalted(),
                "mode", props.getSystem().getMode().name()
        );
    }
}
