package com.trading.aiscalptrader.strategy;

import com.trading.aiscalptrader.domain.model.Candle;

import java.util.List;

/**
 * Pure-function technical indicators. All take double[] series and return
 * either the latest value or an array of values aligned to the input.
 *
 * Implementations match conventional formulas — see PART 4 of spec for the
 * exact thresholds and parameters used by the strategy.
 */
public final class Indicators {

    private Indicators() {}

    /* ---------------- EMA ---------------- */

    public static double[] ema(double[] values, int period) {
        if (values.length < period) return new double[0];
        double[] out = new double[values.length];
        double k = 2.0 / (period + 1);
        // seed with SMA of first `period`
        double sum = 0;
        for (int i = 0; i < period; i++) sum += values[i];
        out[period - 1] = sum / period;
        for (int i = 0; i < period - 1; i++) out[i] = Double.NaN;
        for (int i = period; i < values.length; i++) {
            out[i] = (values[i] - out[i - 1]) * k + out[i - 1];
        }
        return out;
    }

    public static double emaLatest(double[] values, int period) {
        double[] e = ema(values, period);
        return e.length == 0 ? Double.NaN : e[e.length - 1];
    }

    /* ---------------- RSI ---------------- */

    public static double rsi(double[] closes, int period) {
        if (closes.length < period + 1) return Double.NaN;
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = closes[i] - closes[i - 1];
            if (diff > 0) gain += diff; else loss -= diff;
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;
        for (int i = period + 1; i < closes.length; i++) {
            double diff = closes[i] - closes[i - 1];
            double up = Math.max(diff, 0);
            double dn = Math.max(-diff, 0);
            avgGain = (avgGain * (period - 1) + up) / period;
            avgLoss = (avgLoss * (period - 1) + dn) / period;
        }
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /* ---------------- ATR ---------------- */

    public static double atr(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return Double.NaN;
        double[] tr = new double[candles.size()];
        for (int i = 1; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double prevClose = candles.get(i - 1).close();
            double a = c.high() - c.low();
            double b = Math.abs(c.high() - prevClose);
            double cc = Math.abs(c.low() - prevClose);
            tr[i] = Math.max(a, Math.max(b, cc));
        }
        // Wilder's smoothing
        double atr = 0;
        for (int i = 1; i <= period; i++) atr += tr[i];
        atr /= period;
        for (int i = period + 1; i < tr.length; i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
        }
        return atr;
    }

    /* ---------------- ATR series for percentile filter ---------------- */

    public static double[] atrSeries(List<Candle> candles, int period) {
        int n = candles.size();
        if (n < period + 1) return new double[0];
        double[] tr = new double[n];
        for (int i = 1; i < n; i++) {
            Candle c = candles.get(i);
            double prevClose = candles.get(i - 1).close();
            tr[i] = Math.max(c.high() - c.low(),
                    Math.max(Math.abs(c.high() - prevClose), Math.abs(c.low() - prevClose)));
        }
        double[] out = new double[n];
        double atr = 0;
        for (int i = 1; i <= period; i++) atr += tr[i];
        atr /= period;
        out[period] = atr;
        for (int i = period + 1; i < n; i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
            out[i] = atr;
        }
        return out;
    }

    /* ---------------- MACD ---------------- */

    public static double macdHist(double[] closes, int fast, int slow, int signal) {
        double[] emaFast = ema(closes, fast);
        double[] emaSlow = ema(closes, slow);
        if (emaFast.length == 0 || emaSlow.length == 0) return Double.NaN;
        int n = closes.length;
        double[] macd = new double[n];
        for (int i = 0; i < n; i++) macd[i] = emaFast[i] - emaSlow[i];
        // Signal line over MACD where both EMAs valid
        int firstValid = slow - 1;
        double[] valid = new double[n - firstValid];
        System.arraycopy(macd, firstValid, valid, 0, valid.length);
        double[] sig = ema(valid, signal);
        if (sig.length == 0) return Double.NaN;
        double m = macd[n - 1];
        double s = sig[sig.length - 1];
        return m - s;
    }

    /** Compute current and previous MACD histogram so we can detect acceleration. */
    public static double[] macdHistTail(double[] closes, int fast, int slow, int signal) {
        if (closes.length < slow + signal + 1) return new double[]{Double.NaN, Double.NaN};
        double[] curr = new double[closes.length];
        double[] prev = new double[closes.length - 1];
        System.arraycopy(closes, 0, prev, 0, closes.length - 1);
        double h = macdHist(closes, fast, slow, signal);
        double hPrev = macdHist(prev, fast, slow, signal);
        return new double[]{h, hPrev};
    }

    /* ---------------- VWAP (session) ---------------- */

    /**
     * Cumulative session VWAP from start of the day's candles.
     * Returns the latest VWAP given the cumulative sums.
     */
    public static double vwap(List<Candle> sessionCandles) {
        double pv = 0, v = 0;
        for (Candle c : sessionCandles) {
            pv += c.typicalPrice() * c.volume();
            v += c.volume();
        }
        return v == 0 ? Double.NaN : pv / v;
    }

    /* ---------------- Supertrend ---------------- */

    /** Returns +1 (bullish) or -1 (bearish). NaN if not enough data. */
    public static int supertrend(List<Candle> candles, int period, double mult) {
        int n = candles.size();
        if (n < period + 1) return 0;
        double atr = atr(candles, period);
        if (Double.isNaN(atr)) return 0;
        // Iterate to track band switches
        double prevUpper = Double.MAX_VALUE;
        double prevLower = -Double.MAX_VALUE;
        int dir = 1;
        for (int i = period; i < n; i++) {
            Candle c = candles.get(i);
            double hl2 = (c.high() + c.low()) / 2.0;
            double upper = hl2 + mult * atr;
            double lower = hl2 - mult * atr;
            // band reduction
            if (i > period) {
                upper = Math.min(upper, prevUpper);
                lower = Math.max(lower, prevLower);
            }
            if (c.close() > prevUpper) dir = 1;
            else if (c.close() < prevLower) dir = -1;
            prevUpper = upper;
            prevLower = lower;
        }
        return dir;
    }
}
