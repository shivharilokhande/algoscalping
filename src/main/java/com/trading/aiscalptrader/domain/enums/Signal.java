package com.trading.aiscalptrader.domain.enums;

/** Direction signal returned by strategy. HOLD = no trade. */
public enum Signal {
    BUY_CE,   // bullish — buy Call
    BUY_PE,   // bearish — buy Put
    HOLD;

    public boolean isActionable() {
        return this != HOLD;
    }

    public OptionType toOptionType() {
        return switch (this) {
            case BUY_CE -> OptionType.CE;
            case BUY_PE -> OptionType.PE;
            default -> throw new IllegalStateException("HOLD has no option type");
        };
    }
}
