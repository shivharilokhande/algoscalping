package com.trading.aiscalptrader.config;

import lombok.Data;

/** Zerodha Kite Connect credentials. Refresh access token daily via generate_token script. */
@Data
public class KiteProperties {
    private String apiKey = "";
    private String apiSecret = "";
    private String accessToken = "";
    private String requestToken = "";
    private String userId = "";

    public boolean hasCredentials() {
        return !apiKey.isBlank() && !accessToken.isBlank();
    }
}
