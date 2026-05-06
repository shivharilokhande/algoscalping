package com.trading.aiscalptrader.api.dto;

import com.trading.aiscalptrader.domain.model.Trade;

import java.time.Instant;
import java.util.Set;

public record TradeDto(
        String id,
        String symbol,
        String underlying,
        String type,
        int lots,
        double entry,
        Double exit,
        Double pnl,
        Set<String> setups,
        double confidence,
        String reason,
        boolean trailing,
        Instant entryTime,
        Instant exitTime
) {
    public static TradeDto of(Trade t) {
        return new TradeDto(
                t.getId(),
                t.getContract().tradingSymbol(),
                t.getUnderlyingSymbol(),
                t.getOptionType().name(),
                t.getLots(),
                t.getEntryPrice(),
                t.getExitPrice(),
                t.isClosed() ? t.getPnl() : null,
                t.getSetupNames(),
                t.getSignalConfidence(),
                t.getExitReason() != null ? t.getExitReason().name() : "OPEN",
                t.isTrailingActive(),
                t.getEntryTime(),
                t.getExitTime()
        );
    }
}
