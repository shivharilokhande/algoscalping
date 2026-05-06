package com.trading.aiscalptrader.domain.strategy;

import com.trading.aiscalptrader.domain.enums.Signal;
import com.trading.aiscalptrader.domain.enums.SetupType;

import java.time.Instant;

/** A single setup's vote within the rolling 5-bar confluence window. */
public record SetupVote(
        SetupType setup,
        Signal direction,
        double confidence,
        Instant barTime,
        long barIndex,
        String reason
) { }
