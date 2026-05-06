package com.trading.aiscalptrader.domain.model;

import lombok.Builder;

import java.time.Instant;

/** Result of placing or filling an order. */
@Builder
public record OrderResult(
        String orderId,
        String exchangeOrderId,
        String tradingSymbol,
        String status,
        int quantity,
        double averagePrice,
        Instant timestamp,
        String message
) {
    public boolean isFilled() {
        return "COMPLETE".equalsIgnoreCase(status);
    }

    /** True when the broker has accepted the order (it may still be unfilled). */
    public boolean isAccepted() {
        if (status == null) return false;
        return switch (status.toUpperCase()) {
            case "COMPLETE", "OPEN", "TRIGGER PENDING", "PENDING" -> true;
            default -> false;
        };
    }
}
