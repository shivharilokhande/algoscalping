package com.trading.aiscalptrader.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.strategy.DayPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * F-041 / F-042 — calls Anthropic Claude for pre-market analysis and post-market
 * review. Disabled by default; set autoscalp.ai.enabled=true and ANTHROPIC_API_KEY.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeAdvisor {

    private final AutoScalpProperties props;
    private final WebClient anthropicWebClient;
    private final ObjectMapper mapper;

    private static final String SYSTEM_PROMPT =
            "You are a quantitative trading analyst for Indian equity markets. " +
            "You analyze recent BankNIFTY / NIFTY / FinNIFTY trades and produce a JSON-only " +
            "day plan for tomorrow's session. Respond with ONLY a JSON object, no commentary.";

    public DayPlan generateDayPlan(String recentTradesJson) {
        if (!props.getAi().isEnabled()) {
            return defaultPlan("AI disabled");
        }
        try {
            String userPrompt = """
                Below are the last 5 trading days of executed trades from the AutoScalp system.
                Produce a day plan as STRICT JSON with these fields:
                  market_outlook (BULLISH|BEARISH|SIDEWAYS|VOLATILE),
                  confidence (0-1),
                  allocation_adjustment (0.5-1.5),
                  preferred_direction (CE|PE|BOTH),
                  avoid_instruments (array of strings),
                  risk_level (LOW|NORMAL|HIGH),
                  max_trades_override (null or integer 1-5),
                  special_notes (string),
                  events_today (array of strings).
                Trades JSON: %s
                """.formatted(recentTradesJson);

            Map<String, Object> body = Map.of(
                    "model", props.getAi().getModel(),
                    "max_tokens", 1024,
                    "system", SYSTEM_PROMPT,
                    "messages", List.of(Map.of("role", "user", "content", userPrompt))
            );

            JsonNode resp = anthropicWebClient.post()
                    .uri("/v1/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (resp == null) return defaultPlan("Null response");
            String text = resp.path("content").path(0).path("text").asText("");
            String json = extractJson(text);
            DayPlan plan = mapper.readValue(json, DayPlan.class);
            log.info("[CLAUDE] Day plan generated: {}", plan.marketOutlook());
            return plan;
        } catch (Exception e) {
            log.error("Claude pre-market call failed: {}", e.getMessage(), e);
            return defaultPlan("call failed: " + e.getMessage());
        }
    }

    public String reviewTrades(String tradesJson) {
        if (!props.getAi().isEnabled()) return "AI disabled";
        try {
            String prompt = """
                Review today's trades and grade each A-F.
                Identify recurring patterns. Suggest 1-3 parameter changes.
                Trades: %s
                """.formatted(tradesJson);
            Map<String, Object> body = Map.of(
                    "model", props.getAi().getModel(),
                    "max_tokens", 2048,
                    "system", SYSTEM_PROMPT,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            JsonNode resp = anthropicWebClient.post()
                    .uri("/v1/messages").bodyValue(body)
                    .retrieve().bodyToMono(JsonNode.class).block();
            return resp == null ? "" : resp.path("content").path(0).path("text").asText("");
        } catch (Exception e) {
            log.error("Claude post-market call failed: {}", e.getMessage());
            return "review failed: " + e.getMessage();
        }
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start < 0 || end < 0) ? "{}" : text.substring(start, end + 1);
    }

    private DayPlan defaultPlan(String note) {
        return new DayPlan(LocalDate.now().toString(), Instant.now(),
                "SIDEWAYS", 0.5, 1.0, "BOTH",
                List.of(), "NORMAL", null, note, List.of());
    }
}
