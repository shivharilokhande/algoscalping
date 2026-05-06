package com.trading.aiscalptrader.domain.model;

import com.trading.aiscalptrader.domain.enums.OptionType;
import lombok.Builder;

import java.time.LocalDate;

/**
 * Selected option contract (NFO weekly expiry).
 * Symbol example: BANKNIFTY26APR1748000CE
 */
@Builder
public record OptionContract(
        String tradingSymbol,
        long instrumentToken,
        long underlyingToken,
        String underlyingSymbol,
        OptionType optionType,
        double strike,
        LocalDate expiry,
        int lotSize,
        double estimatedPremium,
        double underlyingPrice,
        double delta
) { }
