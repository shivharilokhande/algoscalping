package com.trading.aiscalptrader.config;

import lombok.Data;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

/** Options chain selection params. Lot sizes per NSE Jan 2026 revision. */
@Data
public class OptionsProperties {
    // NSE Jan 2026 revision (spec PART 2.3)
    private Map<Long, Integer> lotSize = new HashMap<>(Map.of(
            256265L, 65,    // NIFTY
            260105L, 30,    // BANKNIFTY
            257801L, 60     // FINNIFTY (was 40 pre-Jan-2026)
    ));
    private Map<Long, Integer> strikeInterval = new HashMap<>(Map.of(
            256265L, 50,
            260105L, 100,
            257801L, 50
    ));
    private Map<Long, DayOfWeek> expiryWeekday = new HashMap<>(Map.of(
            256265L, DayOfWeek.THURSDAY,
            260105L, DayOfWeek.WEDNESDAY,
            257801L, DayOfWeek.TUESDAY
    ));
    private boolean preferItm = true;
    private double preferredDelta = 0.5;
    private double riskFreeRate = 0.07;          // 7% Indian risk-free
    private double annualVolatilityDefault = 0.18;
}
