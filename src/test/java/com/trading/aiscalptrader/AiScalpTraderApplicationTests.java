package com.trading.aiscalptrader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.trading.aiscalptrader.config.AutoScalpProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight context-load smoke test.
 *
 * - autoscalp.scheduling-enabled=false disables @Scheduled fan-out so PaperDataFeed
 *   doesn't tick during tests.
 * - autoscalp.system.pid-file points to a temp path so PidLockfile doesn't collide
 *   with a real running instance.
 * - autoscalp.system.heartbeat-file similarly redirected.
 */
@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "autoscalp.system.pid-file=target/test-autoscalp.pid",
        "autoscalp.system.heartbeat-file=target/test-heartbeat.txt",
        "autoscalp.system.state-file=target/test-state.json"
})
class AiScalpTraderApplicationTests {

    @Autowired AutoScalpProperties props;

    @Test
    void contextLoads() {
        assertThat(props.getRisk().getMaxSlPerDay()).isEqualTo(2);
        assertThat(props.getRisk().getOptionSlPct()).isEqualTo(0.25);
        assertThat(props.getOptions().getLotSize().get(260105L)).isEqualTo(30); // BankNIFTY
    }
}
