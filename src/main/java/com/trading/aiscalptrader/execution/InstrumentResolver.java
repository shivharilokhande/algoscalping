package com.trading.aiscalptrader.execution;

import com.trading.aiscalptrader.data.KiteConnectFactory;
import com.trading.aiscalptrader.domain.enums.OptionType;
import com.trading.aiscalptrader.domain.model.OptionContract;
import com.zerodhatech.models.Instrument;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves a constructed {@link OptionContract} to the actual Kite instrument
 * (token + tradingSymbol). The Kite NFO instrument list is dumped daily and
 * cached in memory.
 *
 * Live mode only — paper mode keeps the constructed symbol.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "autoscalp.system.mode", havingValue = "LIVE")
@RequiredArgsConstructor
public class InstrumentResolver {

    private final KiteConnectFactory kiteFactory;
    private final AtomicReference<List<Instrument>> nfoCache = new AtomicReference<>();

    @PostConstruct
    public void warm() { refresh(); }

    /** Refresh once a day (after market open is fine — we have the cache). */
    @Scheduled(cron = "0 30 8 * * MON-FRI")
    public void refresh() {
        try {
            List<Instrument> all = kiteFactory.kiteConnect().getInstruments("NFO");
            nfoCache.set(all);
            log.info("Loaded {} NFO instruments from Kite", all.size());
        } catch (Exception e) {
            log.error("Failed to load NFO instruments: {}", e.getMessage());
        }
    }

    /** Find best matching instrument; returns the input contract enriched with token, or empty if no match. */
    public Optional<OptionContract> resolve(OptionContract c) {
        List<Instrument> cache = nfoCache.get();
        if (cache == null) return Optional.empty();
        String wantUnderlying = OptionChainSelector.prefix(c.underlyingToken());
        OptionType type = c.optionType();
        LocalDate expiry = c.expiry();
        double strike = c.strike();

        return cache.stream()
                .filter(i -> wantUnderlying.equals(i.name))
                .filter(i -> type.name().equals(i.instrument_type))
                .filter(i -> i.expiry != null && i.expiry.toInstant()
                        .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                        .toLocalDate().equals(expiry))
                .filter(i -> {
                    try {
                        return Math.abs(Double.parseDouble(i.strike) - strike) < 0.5;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                })
                .findFirst()
                .map(i -> OptionContract.builder()
                        .tradingSymbol(i.tradingsymbol)
                        .instrumentToken(i.instrument_token)
                        .underlyingToken(c.underlyingToken())
                        .underlyingSymbol(c.underlyingSymbol())
                        .optionType(c.optionType())
                        .strike(c.strike())
                        .expiry(c.expiry())
                        .lotSize(i.lot_size > 0 ? i.lot_size : c.lotSize())
                        .estimatedPremium(c.estimatedPremium())
                        .delta(c.delta())
                        .underlyingPrice(c.underlyingPrice())
                        .build());
    }
}
