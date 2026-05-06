package com.trading.aiscalptrader.data;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.TradingMode;
import com.trading.aiscalptrader.domain.model.Tick;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.ticker.OnConnect;
import com.zerodhatech.ticker.OnDisconnect;
import com.zerodhatech.ticker.OnError;
import com.zerodhatech.ticker.OnTicks;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connects to the Kite WebSocket via the official Java SDK.
 * Subscribes to NIFTY/BankNIFTY/FinNIFTY in FULL mode (depth + bid/ask + volume).
 * Translates each KiteTick → domain Tick → DataEngine.
 *
 * Auto-reconnects on disconnect (NFR-002).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KiteTickerClient {

    private final AutoScalpProperties props;
    private final DataEngine dataEngine;
    private final KiteConnectFactory kiteFactory;

    private KiteTicker ticker;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void initIfLive() {
        if (props.getSystem().getMode() == TradingMode.LIVE && props.getKite().hasCredentials()) {
            connect();
        } else {
            log.info("KiteTicker NOT started — mode={} or credentials missing. Use PaperDataFeed for paper mode.",
                    props.getSystem().getMode());
        }
    }

    public synchronized void connect() {
        if (running.get()) {
            log.warn("KiteTicker already running");
            return;
        }
        try {
            KiteConnect kc = kiteFactory.kiteConnect();
            ticker = new KiteTicker(kc.getAccessToken(), kc.getApiKey());
            ticker.setOnConnectedListener(onConnect);
            ticker.setOnDisconnectedListener(onDisconnect);
            ticker.setOnErrorListener(onError);
            ticker.setOnTickerArrivalListener(onTicks);
            ticker.setTryReconnection(true);
            ticker.setMaximumRetries(50);
            ticker.setMaximumRetryInterval(60);
            ticker.connect();
            running.set(true);
            log.info("KiteTicker connect() initiated");
        } catch (Exception e) {
            log.error("KiteTicker connect failed: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public synchronized void disconnect() {
        if (ticker != null && running.get()) {
            try { ticker.disconnect(); } catch (Exception ignore) {}
            running.set(false);
            log.info("KiteTicker disconnected");
        }
    }

    private final OnConnect onConnect = () -> {
        log.info("KiteTicker connected — subscribing to {}", props.getSystem().getInstruments());
        ArrayList<Long> tokens = new ArrayList<>(props.getSystem().getInstruments());
        ticker.subscribe(tokens);
        ticker.setMode(tokens, KiteTicker.modeFull);
    };

    private final OnDisconnect onDisconnect = () -> {
        log.warn("KiteTicker disconnected — relying on auto-reconnect");
    };

    private final OnError onError = new OnError() {
        @Override public void onError(Exception e) { log.error("KiteTicker error: {}", e.getMessage(), e); }
        @Override public void onError(com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ke) {
            log.error("KiteTicker KiteException: code={} msg={}", ke.code, ke.message);
        }
        @Override public void onError(String s) { log.error("KiteTicker error string: {}", s); }
    };

    private final OnTicks onTicks = ticks -> {
        if (ticks == null) return;
        for (com.zerodhatech.models.Tick kt : ticks) {
            Tick tick = mapKite(kt);
            if (tick != null) dataEngine.onTick(tick);
        }
    };

    /** Translate Kite SDK Tick → domain Tick. */
    private Tick mapKite(com.zerodhatech.models.Tick kt) {
        try {
            double bid = 0, ask = 0; long bidQ = 0, askQ = 0;
            if (kt.getMarketDepth() != null) {
                var buy = kt.getMarketDepth().get("buy");
                var sell = kt.getMarketDepth().get("sell");
                if (buy != null && !buy.isEmpty())  { bid = buy.get(0).getPrice();  bidQ = buy.get(0).getQuantity(); }
                if (sell != null && !sell.isEmpty()){ ask = sell.get(0).getPrice(); askQ = sell.get(0).getQuantity(); }
            }
            Instant ts = kt.getTickTimestamp() != null ? kt.getTickTimestamp().toInstant() : Instant.now();
            return new Tick(
                    kt.getInstrumentToken(),
                    kt.getLastTradedPrice(),
                    (long) kt.getVolumeTradedToday(),
                    bid, ask, bidQ, askQ, ts
            );
        } catch (Exception e) {
            log.warn("Failed to map Kite tick: {}", e.getMessage());
            return null;
        }
    }

    public boolean isRunning() { return running.get(); }
}
