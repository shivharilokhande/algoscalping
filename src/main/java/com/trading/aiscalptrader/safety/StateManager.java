package com.trading.aiscalptrader.safety;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.aiscalptrader.config.AutoScalpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Safety #5 / #21 — atomic JSON state persistence (mirrors os.replace pattern).
 * Crash recovery loads state on startup; orchestrator reconciles with broker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateManager {

    private final AutoScalpProperties props;
    private final ObjectMapper mapper;

    public synchronized void save(Map<String, Object> state) {
        Path target = Path.of(props.getSystem().getStateFile()).toAbsolutePath();
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), state);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("State save failed: {}", e.getMessage(), e);
        }
    }

    public Map<String, Object> load() {
        Path target = Path.of(props.getSystem().getStateFile());
        if (!Files.exists(target)) return new HashMap<>();
        try {
            return mapper.readValue(target.toFile(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.error("State load failed: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }
}
