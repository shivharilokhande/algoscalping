package com.trading.aiscalptrader.config;

import lombok.Data;

import java.time.LocalTime;

/** Anthropic Claude integration knobs. */
@Data
public class AiProperties {
    private boolean enabled = false;
    private String apiKey = "";
    private String model = "claude-sonnet-4-5-20250929";
    private LocalTime preMarketTime = LocalTime.of(8, 30);
    private LocalTime postMarketTime = LocalTime.of(16, 0);
    private LocalTime weeklyTime = LocalTime.of(20, 0);
    private String dayPlanFile = "data/day_plan.json";
    private String weeklyPlanFile = "data/weekly_plan.json";
    private String reviewDir = "logs/reviews";
    private int planStaleHours = 12;
}
