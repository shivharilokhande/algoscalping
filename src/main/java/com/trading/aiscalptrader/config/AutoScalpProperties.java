package com.trading.aiscalptrader.config;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Root configuration namespace `autoscalp.*` mirrored from spec PART 2.3.
 * All values are loaded from application.yml or environment variables, then frozen
 * by Spring as immutable singletons (effectively dataclass(frozen=True) in Python).
 */
@Data
@Validated
@ConfigurationProperties(prefix = "autoscalp")
public class AutoScalpProperties {

    @Valid
    private KiteProperties kite = new KiteProperties();

    @Valid
    private SystemProperties system = new SystemProperties();

    @Valid
    private RiskProperties risk = new RiskProperties();

    @Valid
    private StrategyProperties strategy = new StrategyProperties();

    @Valid
    private OptionsProperties options = new OptionsProperties();

    @Valid
    private AiProperties ai = new AiProperties();

    @Valid
    private LoggingProperties logging = new LoggingProperties();
}
