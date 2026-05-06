package com.trading.aiscalptrader.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record StatusDto(
        boolean running,
        String mode,
        boolean marketOpen,
        boolean halted,
        LocalDate sessionDate,
        BigDecimal capital,
        BigDecimal pnl,
        int trades,
        int slHits,
        int slLimit,
        int winners,
        boolean profitTargetHit,
        boolean recoveryUsed,
        int activePositions,
        String dayPlanOutlook,
        String dayPlanRisk
) { }
