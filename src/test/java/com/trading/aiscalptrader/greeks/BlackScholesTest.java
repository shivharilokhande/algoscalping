package com.trading.aiscalptrader.greeks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BlackScholesTest {

    @Test
    void atmCallPriceReasonable() {
        // ATM 1-week BankNIFTY call, IV 18%
        double price = BlackScholes.price(true, 48000, 48000, 7.0/365.0, 0.07, 0.18);
        assertThat(price).isGreaterThan(0).isLessThan(2000);
    }

    @Test
    void deltaCallBetween0And1() {
        double d = BlackScholes.delta(true, 22000, 22050, 7.0/365.0, 0.07, 0.18);
        assertThat(d).isBetween(0.0, 1.0);
    }

    @Test
    void impliedVolRoundTrips() {
        double S = 22000, K = 22000, T = 7.0/365.0, r = 0.07, sigma = 0.20;
        double price = BlackScholes.price(true, S, K, T, r, sigma);
        double iv = BlackScholes.impliedVolatility(true, price, S, K, T, r);
        assertThat(iv).isCloseTo(sigma, within(0.005));
    }
}
