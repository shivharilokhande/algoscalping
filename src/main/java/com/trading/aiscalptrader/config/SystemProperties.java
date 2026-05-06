package com.trading.aiscalptrader.config;

import com.trading.aiscalptrader.domain.enums.TradingMode;
import lombok.Data;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** System-wide config: trading mode, market hours, instruments. */
@Data
public class SystemProperties {
    private TradingMode mode = TradingMode.PAPER;
    private String exchange = "NSE";
    private String timezone = "Asia/Kolkata";
    private LocalTime marketOpen = LocalTime.of(9, 15);
    private LocalTime marketClose = LocalTime.of(15, 30);
    private LocalTime eodCloseTime = LocalTime.of(15, 25);

    private String stateFile = "data/state.json";
    private String pidFile = "data/autoscalp.pid";
    private String heartbeatFile = "data/heartbeat.txt";

    private List<Long> instruments = new ArrayList<>(List.of(256265L, 260105L, 257801L));
    private Map<Long, String> tradingSymbols = new HashMap<>(Map.of(
            256265L, "NIFTY 50",
            260105L, "NIFTY BANK",
            257801L, "NIFTY FIN SERVICE"
    ));

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
