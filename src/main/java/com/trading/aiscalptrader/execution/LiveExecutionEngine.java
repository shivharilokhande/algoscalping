package com.trading.aiscalptrader.execution;

import com.trading.aiscalptrader.data.KiteConnectFactory;
import com.trading.aiscalptrader.domain.model.OptionContract;
import com.trading.aiscalptrader.domain.model.OrderResult;
import com.trading.aiscalptrader.safety.RateLimiter;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import com.zerodhatech.models.Quote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Live order placement via Kite Connect Java SDK. */
@Slf4j
@Service
@ConditionalOnProperty(name = "autoscalp.system.mode", havingValue = "LIVE")
@RequiredArgsConstructor
public class LiveExecutionEngine implements ExecutionEngine {

    private final KiteConnectFactory kiteFactory;
    private final RateLimiter rateLimiter;

    @Override
    public OrderResult placeBuy(OptionContract contract, int lots, double ltp) {
        rateLimiter.acquire();
        try {
            KiteConnect kc = kiteFactory.kiteConnect();
            OrderParams p = new OrderParams();
            p.tradingsymbol = contract.tradingSymbol();
            p.exchange = "NFO";
            p.transactionType = Constants.TRANSACTION_TYPE_BUY;
            p.quantity = lots * contract.lotSize();
            p.product = Constants.PRODUCT_NRML;
            p.orderType = Constants.ORDER_TYPE_LIMIT;
            p.price = round05(ltp * 1.005);
            p.validity = Constants.VALIDITY_DAY;
            Order o = kc.placeOrder(p, Constants.VARIETY_REGULAR);
            log.info("[LIVE BUY] order_id={} {}×{} @ {}", o.orderId, lots, contract.lotSize(), p.price);
            return mapOrder(o, contract);
        } catch (Exception e) {
            log.error("placeBuy failed: {}", e.getMessage(), e);
            return errorResult(contract, e.getMessage());
        }
    }

    @Override
    public OrderResult placeStopLoss(OptionContract contract, int lots, double triggerPrice) {
        rateLimiter.acquire();
        try {
            KiteConnect kc = kiteFactory.kiteConnect();
            OrderParams p = new OrderParams();
            p.tradingsymbol = contract.tradingSymbol();
            p.exchange = "NFO";
            p.transactionType = Constants.TRANSACTION_TYPE_SELL;
            p.quantity = lots * contract.lotSize();
            p.product = Constants.PRODUCT_NRML;
            p.orderType = Constants.ORDER_TYPE_SLM;
            p.triggerPrice = round05(triggerPrice);
            p.validity = Constants.VALIDITY_DAY;
            Order o = kc.placeOrder(p, Constants.VARIETY_REGULAR);
            log.info("[LIVE SL-M] order_id={} trigger={}", o.orderId, p.triggerPrice);
            return mapOrder(o, contract);
        } catch (Exception e) {
            log.error("placeStopLoss failed: {}", e.getMessage(), e);
            return errorResult(contract, e.getMessage());
        }
    }

    @Override
    public boolean cancel(String orderId) {
        rateLimiter.acquire();
        try {
            kiteFactory.kiteConnect().cancelOrder(orderId, Constants.VARIETY_REGULAR);
            return true;
        } catch (Exception e) {
            log.warn("cancel({}) failed: {}", orderId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean modifyStopLoss(String orderId, double newTriggerPrice) {
        rateLimiter.acquire();
        try {
            OrderParams p = new OrderParams();
            p.triggerPrice = round05(newTriggerPrice);
            kiteFactory.kiteConnect().modifyOrder(orderId, p, Constants.VARIETY_REGULAR);
            return true;
        } catch (Exception e) {
            log.warn("modifyStopLoss({}) failed: {}", orderId, e.getMessage());
            return false;
        }
    }

    @Override
    public OrderResult placeSell(OptionContract contract, int lots) {
        rateLimiter.acquire();
        try {
            KiteConnect kc = kiteFactory.kiteConnect();
            OrderParams p = new OrderParams();
            p.tradingsymbol = contract.tradingSymbol();
            p.exchange = "NFO";
            p.transactionType = Constants.TRANSACTION_TYPE_SELL;
            p.quantity = lots * contract.lotSize();
            p.product = Constants.PRODUCT_NRML;
            p.orderType = Constants.ORDER_TYPE_MARKET;
            p.validity = Constants.VALIDITY_DAY;
            Order o = kc.placeOrder(p, Constants.VARIETY_REGULAR);
            log.info("[LIVE SELL] order_id={} {}×{}", o.orderId, lots, contract.lotSize());
            return mapOrder(o, contract);
        } catch (Exception e) {
            log.error("placeSell failed: {}", e.getMessage(), e);
            return errorResult(contract, e.getMessage());
        }
    }

    @Override
    public double getLtp(String tradingSymbol) {
        rateLimiter.acquire();
        try {
            String key = "NFO:" + tradingSymbol;
            Map<String, Quote> q = kiteFactory.kiteConnect().getQuote(new String[]{key});
            Quote quote = q.get(key);
            return quote != null ? quote.lastPrice : 0.0;
        } catch (Exception e) {
            log.warn("getLtp({}) failed: {}", tradingSymbol, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public int getNetQuantity(String tradingSymbol) {
        rateLimiter.acquire();
        try {
            Map<String, List<Position>> positions = kiteFactory.kiteConnect().getPositions();
            List<Position> net = positions.get("net");
            if (net == null) return 0;
            return net.stream()
                    .filter(p -> tradingSymbol.equals(p.tradingSymbol))
                    .mapToInt(p -> (int) p.netQuantity)
                    .sum();
        } catch (Exception e) {
            log.warn("getNetQuantity failed: {}", e.getMessage());
            return 0;
        }
    }

    private double round05(double v) {
        return Math.round(v * 20.0) / 20.0;
    }

    private OrderResult mapOrder(Order o, OptionContract c) {
        return OrderResult.builder()
                .orderId(o.orderId)
                .exchangeOrderId(o.exchangeOrderId)
                .tradingSymbol(c.tradingSymbol())
                .status(o.status)
                .timestamp(Instant.now())
                .build();
    }

    private OrderResult errorResult(OptionContract c, String msg) {
        return OrderResult.builder()
                .tradingSymbol(c.tradingSymbol())
                .status("REJECTED")
                .timestamp(Instant.now())
                .message(msg)
                .build();
    }
}
