package com.trading.aiscalptrader.risk;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.ExitReason;
import com.trading.aiscalptrader.domain.enums.OptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RiskManagerTest {

    private AutoScalpProperties props;
    private RiskManager risk;

    @BeforeEach
    void setUp() {
        props = new AutoScalpProperties();
        props.getRisk().setCapital(new BigDecimal("100000"));
        risk = new RiskManager(props);
        risk.newSession(LocalDate.of(2026, 5, 6));
    }

    @Test
    void firstTradeAllowedAtFullAllocation() {
        RiskDecision d = risk.evaluate(256265L, OptionType.CE, LocalTime.of(10, 0), "NIFTY 50");
        assertThat(d.allowed()).isTrue();
        assertThat(d.allocationPct()).isEqualTo(0.30);
    }

    @Test
    void afterFirstSlAllocationReducesTo20Pct() {
        risk.recordExit(ExitReason.STOP_LOSS, new BigDecimal("-2000"));
        RiskDecision d = risk.evaluate(256265L, OptionType.CE, LocalTime.of(10, 30), "NIFTY 50");
        assertThat(d.allowed()).isTrue();
        assertThat(d.allocationPct()).isEqualTo(0.20);
    }

    @Test
    void twoSlHitsHaltUntilRecoveryWindow() {
        risk.recordExit(ExitReason.STOP_LOSS, new BigDecimal("-2000"));
        risk.recordExit(ExitReason.STOP_LOSS, new BigDecimal("-2000"));
        assertThat(risk.isHalted()).isTrue();
        // before 13:30 → denied
        RiskDecision d1 = risk.evaluate(256265L, OptionType.CE, LocalTime.of(11, 0), "NIFTY 50");
        assertThat(d1.allowed()).isFalse();
        // after 13:30 → allowed at 10% allocation, exactly once
        RiskDecision d2 = risk.evaluate(256265L, OptionType.CE, LocalTime.of(13, 45), "NIFTY 50");
        assertThat(d2.allowed()).isTrue();
        assertThat(d2.allocationPct()).isEqualTo(0.10);
        // second attempt → denied
        RiskDecision d3 = risk.evaluate(256265L, OptionType.CE, LocalTime.of(14, 0), "NIFTY 50");
        assertThat(d3.allowed()).isFalse();
    }

    @Test
    void twentyPercentProfitLockTriggers() {
        risk.recordExit(ExitReason.TAKE_PROFIT, new BigDecimal("21000"));
        RiskDecision d = risk.evaluate(256265L, OptionType.CE, LocalTime.of(11, 0), "NIFTY 50");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("profit target");
    }
}
