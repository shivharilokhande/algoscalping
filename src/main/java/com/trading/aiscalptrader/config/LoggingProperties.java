package com.trading.aiscalptrader.config;

import lombok.Data;

@Data
public class LoggingProperties {
    private String tradesFile = "logs/trades.json";
    private String riskFile = "logs/risk.json";
    private String marketFile = "logs/market.json";
    private boolean rotateDaily = true;
}
