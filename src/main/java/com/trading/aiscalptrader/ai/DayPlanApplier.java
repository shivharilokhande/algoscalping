package com.trading.aiscalptrader.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.strategy.DayPlan;
import com.trading.aiscalptrader.risk.RiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * F-044 — at session start read data/day_plan.json and apply to RiskManager.
 * EXPLICIT FALLBACK: if file missing/stale/invalid, log warning and trade defaults.
 * NEVER silently use yesterday's plan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DayPlanApplier {

    private final AutoScalpProperties props;
    private final ObjectMapper mapper;
    private final RiskManager riskManager;

    public DayPlan applyForToday(LocalDate today) {
        Path file = Path.of(props.getAi().getDayPlanFile());
        if (!Files.exists(file)) {
            log.warn("[DAYPLAN] {} missing — trading with defaults", file);
            return useDefault("missing");
        }
        try {
            DayPlan plan = mapper.readValue(file.toFile(), DayPlan.class);
            // Date check
            if (plan.date() == null || !plan.date().equals(today.toString())) {
                log.warn("[DAYPLAN] Stale plan dated {} (today={}) — IGNORING and using defaults",
                        plan.date(), today);
                return useDefault("stale-date");
            }
            // Generated-at age check
            if (plan.generatedAt() != null) {
                long hours = Duration.between(plan.generatedAt(), Instant.now()).toHours();
                if (hours > props.getAi().getPlanStaleHours()) {
                    log.warn("[DAYPLAN] Plan {}h old > stale threshold — using with caution", hours);
                }
            }
            riskManager.applyDayPlan(plan);
            return plan;
        } catch (Exception e) {
            log.error("[DAYPLAN] parse failed: {} — trading defaults", e.getMessage());
            return useDefault("invalid-json");
        }
    }

    private DayPlan useDefault(String reason) {
        DayPlan p = new DayPlan(LocalDate.now().toString(), Instant.now(),
                "SIDEWAYS", 0.5, 1.0, "BOTH", List.of(), "NORMAL", null, reason, List.of());
        riskManager.applyDayPlan(p);
        return p;
    }
}
