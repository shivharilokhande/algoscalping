package com.trading.aiscalptrader.safety;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.data.KiteConnectFactory;
import com.trading.aiscalptrader.domain.enums.TradingMode;
import com.zerodhatech.models.Profile;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Safety #4 — every 5 minutes verify Kite access token by hitting getProfile. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenHealthMonitor {

    private final AutoScalpProperties props;
    private final KiteConnectFactory kiteFactory;

    @Getter private volatile boolean tokenValid = true;
    @Getter private volatile Instant lastCheck = Instant.EPOCH;

    @Scheduled(fixedDelay = 5 * 60_000)
    public void check() {
        if (props.getSystem().getMode() != TradingMode.LIVE) return;
        if (!props.getKite().hasCredentials()) { tokenValid = false; return; }
        try {
            Profile p = getProfile();
            tokenValid = p != null;
            lastCheck = Instant.now();
        } catch (Exception e) {
            tokenValid = false;
            log.error("[TOKEN] Kite token validation failed: {}", e.getMessage());
        }
    }

    private Profile getProfile() throws KiteException {
        return kiteFactory.kiteConnect().getProfile();
    }
}
