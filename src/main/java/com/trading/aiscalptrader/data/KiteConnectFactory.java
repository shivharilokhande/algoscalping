package com.trading.aiscalptrader.data;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.zerodhatech.kiteconnect.KiteConnect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Lazily builds a singleton KiteConnect using credentials from properties. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KiteConnectFactory {

    private final AutoScalpProperties props;
    private volatile KiteConnect cached;

    public synchronized KiteConnect kiteConnect() {
        if (cached != null) return cached;
        var k = props.getKite();
        if (!k.hasCredentials()) {
            throw new IllegalStateException("Kite credentials missing — set KITE_API_KEY and KITE_ACCESS_TOKEN");
        }
        KiteConnect kc = new KiteConnect(k.getApiKey(), false);
        kc.setUserId(k.getUserId());
        kc.setAccessToken(k.getAccessToken());
        cached = kc;
        log.info("KiteConnect initialized for user {}", k.getUserId());
        return cached;
    }

    public synchronized void refreshAccessToken(String newToken) {
        if (cached != null) cached.setAccessToken(newToken);
        props.getKite().setAccessToken(newToken);
        log.info("Kite access token refreshed");
    }
}
