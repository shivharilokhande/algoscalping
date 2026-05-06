package com.trading.aiscalptrader.risk;

import com.trading.aiscalptrader.domain.enums.ExitReason;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/** Mutable daily counters & flags, persisted by StateManager. */
@Data
public class DailyState {
    private LocalDate date;
    private BigDecimal startCapital = BigDecimal.ZERO;
    private BigDecimal currentCapital = BigDecimal.ZERO;
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    private final AtomicInteger tradesTaken = new AtomicInteger();
    private final AtomicInteger slHits = new AtomicInteger();      // hard stop-loss only (not trailing SL)
    private final AtomicInteger trailHits = new AtomicInteger();
    private final AtomicInteger winners = new AtomicInteger();

    private boolean haltedFor2Sl = false;
    private boolean profitTargetHit = false;
    private boolean recoveryTradeUsed = false;

    public void reset(LocalDate today, BigDecimal capital) {
        this.date = today;
        this.startCapital = capital;
        this.currentCapital = capital;
        this.realizedPnl = BigDecimal.ZERO;
        this.tradesTaken.set(0);
        this.slHits.set(0);
        this.trailHits.set(0);
        this.winners.set(0);
        this.haltedFor2Sl = false;
        this.profitTargetHit = false;
        this.recoveryTradeUsed = false;
    }

    public void recordExit(ExitReason reason, BigDecimal pnl) {
        currentCapital = currentCapital.add(pnl);
        realizedPnl = realizedPnl.add(pnl);
        tradesTaken.incrementAndGet();
        switch (reason) {
            case STOP_LOSS -> slHits.incrementAndGet();
            case TRAILING_SL -> trailHits.incrementAndGet();
            case TAKE_PROFIT -> winners.incrementAndGet();
            default -> { if (pnl.signum() > 0) winners.incrementAndGet(); }
        }
    }
}
