package com.trading.aiscalptrader.safety;

import com.trading.aiscalptrader.config.AutoScalpProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Safety #17 — prevent two AutoScalp instances on the same machine. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PidLockfile {

    private final AutoScalpProperties props;
    private final ApplicationContext ctx;

    @Value("${autoscalp.safety.pid-lock.fail-fast:false}")
    private boolean failFast;

    private Path pidFile;
    private boolean acquired;

    @PostConstruct
    public void acquire() {
        try {
            pidFile = Paths.get(props.getSystem().getPidFile()).toAbsolutePath();
            Path parent = pidFile.getParent();
            if (parent != null) Files.createDirectories(parent);

            if (Files.exists(pidFile)) {
                String existing = Files.readString(pidFile).trim();
                if (isProcessAlive(existing)) {
                    String msg = "Another AutoScalp instance running (pid=" + existing
                            + "). Set autoscalp.safety.pid-lock.fail-fast=true to abort, "
                            + "or remove " + pidFile + " if it's stale.";
                    if (failFast) throw new IllegalStateException(msg);
                    log.error("[PID] {}", msg);
                    return;
                }
            }
            long myPid = ProcessHandle.current().pid();
            Files.writeString(pidFile, Long.toString(myPid));
            acquired = true;
            log.info("PID lockfile acquired: {} (pid={})", pidFile, myPid);
        } catch (IOException e) {
            log.warn("PID lockfile setup failed (continuing): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void release() {
        if (!acquired || pidFile == null) return;
        try { Files.deleteIfExists(pidFile); }
        catch (IOException ignored) {}
    }

    private boolean isProcessAlive(String pidStr) {
        try {
            long pid = Long.parseLong(pidStr);
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (NumberFormatException ignored) { return false; }
    }
}
