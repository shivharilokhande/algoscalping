package com.trading.aiscalptrader.execution;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import com.trading.aiscalptrader.domain.enums.OptionType;
import com.trading.aiscalptrader.domain.model.OptionContract;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OptionChainSelectorTest {

    @Test
    void nextWeekExpiryOnExpiryDay() {
        var props = new AutoScalpProperties();
        var sel = new OptionChainSelector(props);
        // BankNIFTY expiry is Wednesday — pick a Wed
        LocalDate today = LocalDate.of(2026, 5, 6); // Wed
        LocalDate expiry = sel.pickExpiry(260105L, today);
        // On expiry day → next week's Wed
        assertThat(expiry).isEqualTo(LocalDate.of(2026, 5, 13));
    }

    @Test
    void buildsCorrectSymbol() {
        String sym = OptionChainSelector.buildSymbol("BANKNIFTY", LocalDate.of(2026, 4, 17), 48000.0, OptionType.CE);
        assertThat(sym).isEqualTo("BANKNIFTY26APR1748000CE");
    }

    @Test
    void selectsItmStrikeForCe() {
        var props = new AutoScalpProperties();
        var sel = new OptionChainSelector(props);
        OptionContract c = sel.select(260105L, "NIFTY BANK", 48050, OptionType.CE, LocalDate.of(2026, 5, 5));
        // ATM is 48000 (rounded), ITM call ⇒ strike below ATM
        assertThat(c.strike()).isEqualTo(47900.0);
        assertThat(c.optionType()).isEqualTo(OptionType.CE);
    }
}
