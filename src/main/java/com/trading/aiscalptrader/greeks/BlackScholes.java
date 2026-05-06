package com.trading.aiscalptrader.greeks;

import org.apache.commons.math3.distribution.NormalDistribution;

/**
 * Standard Black-Scholes pricing + Greeks + Newton-Raphson IV.
 * Prices in INR. Time in years.
 */
public final class BlackScholes {

    private static final NormalDistribution N = new NormalDistribution();
    private BlackScholes() {}

    public static double price(boolean call, double S, double K, double T, double r, double sigma) {
        if (T <= 0 || sigma <= 0) {
            return call ? Math.max(0, S - K) : Math.max(0, K - S);
        }
        double d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);
        if (call) return S * N.cumulativeProbability(d1) - K * Math.exp(-r * T) * N.cumulativeProbability(d2);
        return K * Math.exp(-r * T) * N.cumulativeProbability(-d2) - S * N.cumulativeProbability(-d1);
    }

    public static double delta(boolean call, double S, double K, double T, double r, double sigma) {
        if (T <= 0 || sigma <= 0) {
            return call ? (S > K ? 1.0 : 0.0) : (S < K ? -1.0 : 0.0);
        }
        double d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        return call ? N.cumulativeProbability(d1) : N.cumulativeProbability(d1) - 1.0;
    }

    public static double gamma(double S, double K, double T, double r, double sigma) {
        if (T <= 0 || sigma <= 0) return 0;
        double d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        return N.density(d1) / (S * sigma * Math.sqrt(T));
    }

    public static double vega(double S, double K, double T, double r, double sigma) {
        if (T <= 0 || sigma <= 0) return 0;
        double d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        return S * N.density(d1) * Math.sqrt(T);
    }

    /**
     * Newton-Raphson implied volatility from market price.
     * Falls back to bisection if Newton fails to converge.
     */
    public static double impliedVolatility(boolean call, double marketPrice, double S, double K,
                                           double T, double r) {
        double sigma = 0.20;
        for (int i = 0; i < 100; i++) {
            double price = price(call, S, K, T, r, sigma);
            double vega = vega(S, K, T, r, sigma);
            if (vega < 1e-8) break;
            double diff = price - marketPrice;
            if (Math.abs(diff) < 1e-4) return sigma;
            sigma -= diff / vega;
            if (sigma <= 0.001) sigma = 0.01;
            if (sigma > 5.0) sigma = 5.0;
        }
        // Bisection fallback
        double lo = 0.001, hi = 5.0;
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2;
            double p = price(call, S, K, T, r, mid);
            if (Math.abs(p - marketPrice) < 1e-3) return mid;
            if (p < marketPrice) lo = mid; else hi = mid;
        }
        return Double.NaN;
    }
}
