package com.trading.aiscalptrader.execution;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.OptionType;
import com.trading.aiscalptrader.domain.model.OptionContract;
import com.trading.aiscalptrader.greeks.BlackScholes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Spec PART 6 — picks ATM/ITM strike, correct weekly expiry,
 * builds tradingsymbol e.g. BANKNIFTY26APR1748000CE.
 *
 * For pure Greeks/premium estimation, we use Black-Scholes when broker quotes
 * aren't available (paper mode, fallback). Live mode should overwrite the
 * estimated premium with kite.getQuote() before order placement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptionChainSelector {

    private static final String[] MONTHS = {"JAN","FEB","MAR","APR","MAY","JUN",
                                            "JUL","AUG","SEP","OCT","NOV","DEC"};

    private final AutoScalpProperties props;

    public OptionContract select(long underlyingToken,
                                 String underlyingSymbolDisplay,
                                 double underlyingPrice,
                                 OptionType type,
                                 LocalDate today) {
        var op = props.getOptions();
        int interval = op.getStrikeInterval().getOrDefault(underlyingToken, 50);
        double atm = Math.round(underlyingPrice / interval) * (double) interval;

        // Slightly ITM preference (delta 0.6-0.7)
        double strike = atm;
        if (op.isPreferItm()) {
            if (type == OptionType.CE) strike = atm - interval;
            else                        strike = atm + interval;
        }

        LocalDate expiry = pickExpiry(underlyingToken, today);
        int lotSize = op.getLotSize().getOrDefault(underlyingToken, 30);
        String prefix = prefix(underlyingToken);
        String symbol = buildSymbol(prefix, expiry, strike, type);

        double T = Math.max(0.5 / 365.0, ChronoUnit.DAYS.between(today, expiry) / 365.0);
        double premium = BlackScholes.price(type.isCall(), underlyingPrice, strike, T,
                op.getRiskFreeRate(), op.getAnnualVolatilityDefault());
        double delta = BlackScholes.delta(type.isCall(), underlyingPrice, strike, T,
                op.getRiskFreeRate(), op.getAnnualVolatilityDefault());

        return OptionContract.builder()
                .tradingSymbol(symbol)
                .instrumentToken(0L) // resolved later via kite.getInstruments()
                .underlyingToken(underlyingToken)
                .underlyingSymbol(underlyingSymbolDisplay)
                .optionType(type)
                .strike(strike)
                .expiry(expiry)
                .lotSize(lotSize)
                .estimatedPremium(premium)
                .delta(delta)
                .underlyingPrice(underlyingPrice)
                .build();
    }

    public LocalDate pickExpiry(long underlyingToken, LocalDate today) {
        DayOfWeek expiryDow = props.getOptions().getExpiryWeekday().getOrDefault(underlyingToken, DayOfWeek.THURSDAY);
        int daysAhead = expiryDow.getValue() - today.getDayOfWeek().getValue();
        if (daysAhead < 0) daysAhead += 7;
        LocalDate thisWeek = today.plusDays(daysAhead);
        // On expiry day OR day before — roll to next week (F-036)
        if (today.equals(thisWeek) || today.equals(thisWeek.minusDays(1))) {
            return thisWeek.plusDays(7);
        }
        return thisWeek;
    }

    public static String buildSymbol(String prefix, LocalDate expiry, double strike, OptionType type) {
        int year = expiry.getYear() % 100;
        String mon = MONTHS[expiry.getMonthValue() - 1];
        int day = expiry.getDayOfMonth();
        return String.format("%s%02d%s%02d%d%s", prefix, year, mon, day, (int) strike, type.name());
    }

    public static String prefix(long underlyingToken) {
        if (underlyingToken == 256265L) return "NIFTY";
        if (underlyingToken == 260105L) return "BANKNIFTY";
        if (underlyingToken == 257801L) return "FINNIFTY";
        return "UNKNOWN";
    }
}
