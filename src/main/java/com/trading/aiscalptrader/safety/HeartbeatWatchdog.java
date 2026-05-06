package com.trading.aiscalptrader.safety;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Safety #6 / #32 — write a timestamp every 10s. External cron monitors this. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatWatchdog {

    private final AutoScalpProperties props;

    @Scheduled(fixedDelay = 10_000)
    public void beat() {
        try {
            Path p = Path.of(props.getSystem().getHeartbeatFile()).toAbsolutePath();
            Path parent = p.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(p, Instant.now().toString());
        } catch (IOException e) {
            log.warn("Heartbeat write failed: {}", e.getMessage());
        }
    }
}
