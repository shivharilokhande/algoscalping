package com.trading.aiscalptrader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
public class AiScalpTraderApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(AiScalpTraderApplication.class, args);
    }

    /**
     * Reads the project-root .env file (if present) and copies each KEY=VALUE pair
     * into JVM system properties BEFORE Spring boots, so application.properties
     * placeholders like ${KITE_API_KEY:} resolve cleanly without having to
     * `source .env` in the shell.
     *
     * Order of precedence (Spring resolves first non-blank):
     *   1. Real OS environment variables
     *   2. -Dkey=value system properties (set by this loader from .env)
     *   3. application.properties defaults
     */
    private static void loadDotEnv() {
        for (Path candidate : new Path[]{Paths.get(".env"), Paths.get("../.env")}) {
            if (Files.exists(candidate)) {
                try {
                    List<String> lines = Files.readAllLines(candidate);
                    int loaded = 0;
                    for (String raw : lines) {
                        String line = raw.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq <= 0) continue;
                        String key = line.substring(0, eq).trim();
                        String val = line.substring(eq + 1).trim();
                        if ((val.startsWith("\"") && val.endsWith("\""))
                                || (val.startsWith("'") && val.endsWith("'"))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        // Don't override real OS env vars or already-set sys props
                        if (System.getenv(key) == null && System.getProperty(key) == null) {
                            System.setProperty(key, val);
                            loaded++;
                        }
                    }
                    System.out.println("[dotenv] loaded " + loaded + " keys from "
                            + candidate.toAbsolutePath());
                    return;
                } catch (IOException e) {
                    System.err.println("[dotenv] failed to read " + candidate + ": " + e.getMessage());
                }
            }
        }
        // No .env file — that's fine, env vars or app properties will provide values
    }
}
