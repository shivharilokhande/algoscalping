package com.trading.aiscalptrader.domain.strategy;

import com.trading.aiscalptrader.domain.enums.Signal;
import lombok.Builder;

import java.util.List;
import java.util.Set;

/**
 * Output of the high-win-rate strategy evaluation per instrument per 1m candle.
 * Mirrors strategy_highwr.evaluate() return contract.
 */
@Builder
public record HighWRResult(
        Signal signal,
        double confidence,
        Set<String> confluenceSetups,        // unique setup names agreeing
        List<SetupVote> recentVotes,
        boolean layer1Passed,
        boolean layer2Passed,
        int layer2Score,                      // count of confirmations passed (out of 4)
        Layer2Status layer2Status,
        String reason
) {
    public boolean isExecutable() {
        return signal != Signal.HOLD && layer1Passed && layer2Passed;
    }

    public int confluenceCount() {
        return confluenceSetups == null ? 0 : confluenceSetups.size();
    }

    public static HighWRResult hold(String reason) {
        return HighWRResult.builder()
                .signal(Signal.HOLD)
                .confidence(0.0)
                .confluenceSetups(Set.of())
                .recentVotes(List.of())
                .layer1Passed(false)
                .layer2Passed(false)
                .layer2Score(0)
                .layer2Status(new Layer2Status(false, false, false, false))
                .reason(reason)
                .build();
    }
}
