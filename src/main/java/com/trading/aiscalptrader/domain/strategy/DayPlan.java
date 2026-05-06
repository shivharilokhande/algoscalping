package com.trading.aiscalptrader.domain.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trading.aiscalptrader.domain.enums.OptionType;

import java.time.Instant;
import java.util.List;

/** Claude AI pre-market plan, persisted to data/day_plan.json. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DayPlan(
        @JsonProperty("date") String date,                          // YYYY-MM-DD
        @JsonProperty("generated_at") Instant generatedAt,
        @JsonProperty("market_outlook") String marketOutlook,       // BULLISH|BEARISH|SIDEWAYS|VOLATILE
        @JsonProperty("confidence") double confidence,
        @JsonProperty("allocation_adjustment") double allocationAdjustment, // 0.5 - 1.5
        @JsonProperty("preferred_direction") String preferredDirection,    // CE|PE|BOTH
        @JsonProperty("avoid_instruments") List<String> avoidInstruments,
        @JsonProperty("risk_level") String riskLevel,               // LOW|NORMAL|HIGH
        @JsonProperty("max_trades_override") Integer maxTradesOverride,
        @JsonProperty("special_notes") String specialNotes,
        @JsonProperty("events_today") List<String> eventsToday
) {
    public boolean prefersDirection(OptionType type) {
        if (preferredDirection == null || "BOTH".equalsIgnoreCase(preferredDirection)) return true;
        return preferredDirection.equalsIgnoreCase(type.name());
    }

    public boolean isHighRisk() {
        return "HIGH".equalsIgnoreCase(riskLevel);
    }
}
