package com.trading.aiscalptrader.domain.enums;

import java.time.Duration;

public enum CandleInterval {
    ONE_MIN(Duration.ofMinutes(1)),
    FIVE_MIN(Duration.ofMinutes(5)),
    FIFTEEN_MIN(Duration.ofMinutes(15));

    private final Duration duration;

    CandleInterval(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
