package com.trading.aiscalptrader.risk;

/** Result of risk check before placing a trade. */
public record RiskDecision(boolean allowed, double allocationPct, String reason) {
    public static RiskDecision deny(String reason) {
        return new RiskDecision(false, 0.0, reason);
    }
    public static RiskDecision allow(double pct, String reason) {
        return new RiskDecision(true, pct, reason);
    }
}
