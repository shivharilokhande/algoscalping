package com.trading.aiscalptrader.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.model.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes JSON-Lines events to logs/trades.json and logs/risk.json (NFR-004). */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeJournal {

    private final AutoScalpProperties props;
    private final ObjectMapper mapper;

    public synchronized void recordEntry(Trade trade) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("event", "ENTRY");
        rec.put("ts", Instant.now().toString());
        rec.put("trade_id", trade.getId());
        rec.put("symbol", trade.getContract().tradingSymbol());
        rec.put("underlying", trade.getUnderlyingSymbol());
        rec.put("type", trade.getOptionType().name());
        rec.put("strike", trade.getContract().strike());
        rec.put("expiry", trade.getContract().expiry().toString());
        rec.put("lots", trade.getLots());
        rec.put("qty", trade.getQuantity());
        rec.put("entry_price", trade.getEntryPrice());
        rec.put("setups", trade.getSetupNames());
        rec.put("confidence", trade.getSignalConfidence());
        rec.put("allocation_pct", trade.getAllocationPct());
        write(props.getLogging().getTradesFile(), rec);
    }

    public synchronized void recordExit(Trade trade) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("event", "EXIT");
        rec.put("ts", trade.getExitTime() != null ? trade.getExitTime().toString() : Instant.now().toString());
        rec.put("trade_id", trade.getId());
        rec.put("symbol", trade.getContract().tradingSymbol());
        rec.put("underlying", trade.getUnderlyingSymbol());
        rec.put("type", trade.getOptionType().name());
        rec.put("entry", trade.getEntryPrice());
        rec.put("exit", trade.getExitPrice());
        rec.put("pnl", trade.getPnl());
        rec.put("reason", trade.getExitReason() != null ? trade.getExitReason().name() : null);
        rec.put("trailing", trade.isTrailingActive());
        write(props.getLogging().getRiskFile(), rec);
    }

    private void write(String path, Map<String, Object> record) {
        try {
            Path p = Path.of(path).toAbsolutePath();
            Path parent = p.getParent();
            if (parent != null) Files.createDirectories(parent);
            String line = mapper.writeValueAsString(record) + "\n";
            Files.writeString(p, line, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Journal write failed: {}", e.getMessage());
        }
    }
}
