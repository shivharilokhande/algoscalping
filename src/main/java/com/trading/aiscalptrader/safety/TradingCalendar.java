package com.trading.aiscalptrader.safety;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/** Safety #8 — NSE holiday calendar. Bake in known 2026 holidays. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingCalendar {

    private final AutoScalpProperties props;

    private static final Set<LocalDate> HOLIDAYS_2026 = Set.of(
            LocalDate.of(2026, 1, 26),  // Republic Day
            LocalDate.of(2026, 3, 6),   // Holi
            LocalDate.of(2026, 3, 19),  // Eid-Ul-Fitr (estimated)
            LocalDate.of(2026, 4, 3),   // Good Friday
            LocalDate.of(2026, 4, 14),  // Ambedkar Jayanti
            LocalDate.of(2026, 5, 1),   // Maharashtra Day
            LocalDate.of(2026, 8, 15),  // Independence Day (Saturday)
            LocalDate.of(2026, 10, 2),  // Gandhi Jayanti
            LocalDate.of(2026, 11, 11), // Diwali Laxmi Pujan (estimated)
            LocalDate.of(2026, 12, 25)  // Christmas
    );

    /** Special-event days reduce allocation to 15% (F-037). */
    private static final Set<LocalDate> SPECIAL_EVENTS_2026 = Set.of(
            LocalDate.of(2026, 2, 1),   // Budget day
            LocalDate.of(2026, 4, 9),   // RBI MPC (estimated)
            LocalDate.of(2026, 6, 5),   // RBI MPC
            LocalDate.of(2026, 8, 7),   // RBI MPC
            LocalDate.of(2026, 10, 1)   // RBI MPC
    );

    public boolean isTradingDay(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) return false;
        return !HOLIDAYS_2026.contains(date);
    }

    public boolean isMarketOpen() {
        ZoneId z = props.getSystem().zoneId();
        LocalDate today = LocalDate.now(z);
        if (!isTradingDay(today)) return false;
        LocalTime now = LocalTime.now(z);
        return !now.isBefore(props.getSystem().getMarketOpen())
                && !now.isAfter(props.getSystem().getMarketClose());
    }

    public boolean isSpecialEvent(LocalDate date) {
        return SPECIAL_EVENTS_2026.contains(date);
    }
}
