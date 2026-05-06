package com.trading.aiscalptrader.tools;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone helper to refresh the daily Kite access token.
 *
 * Usage:
 *   ./mvnw -q exec:java -Dexec.mainClass=com.trading.aiscalptrader.tools.TokenGenerator
 *
 * Steps:
 *   1. Reads KITE_API_KEY / KITE_API_SECRET from .env
 *   2. Prints the Kite login URL
 *   3. You log in → Zerodha redirects to your redirect URL with ?request_token=XXXX
 *   4. Paste the request_token here
 *   5. Helper exchanges it for an access_token, writes back into .env
 *
 * Has zero Spring dependencies — runs as a plain main().
 */
public final class TokenGenerator {

    public static void main(String[] args) throws Exception {
        String apiKey = env("KITE_API_KEY");
        String apiSecret = env("KITE_API_SECRET");
        if (apiKey == null || apiSecret == null) {
            System.err.println("Set KITE_API_KEY and KITE_API_SECRET in .env first.");
            System.exit(1);
        }

        String loginUrl = "https://kite.zerodha.com/connect/login?api_key=" + apiKey + "&v=3";
        System.out.println();
        System.out.println("=== AutoScalp daily token refresh ===");
        System.out.println("1. Open this URL in your browser and log in:");
        System.out.println();
        System.out.println("   " + loginUrl);
        System.out.println();
        System.out.println("2. After login, your browser will redirect to your registered redirect URL");
        System.out.println("   with a parameter ?request_token=XXXXXXXXXXXX");
        System.out.println();
        System.out.print("Paste the request_token here: ");

        try (Scanner sc = new Scanner(System.in)) {
            String reqToken = sc.nextLine().trim();
            if (reqToken.isBlank()) {
                System.err.println("Empty token. Aborting.");
                System.exit(1);
            }

            KiteConnect kc = new KiteConnect(apiKey, false);
            User user = kc.generateSession(reqToken, apiSecret);
            String accessToken = user.accessToken;
            System.out.println();
            System.out.println("Access token: " + accessToken);
            System.out.println("User: " + user.userName + " (" + user.userId + ")");

            updateEnv("KITE_ACCESS_TOKEN", accessToken);
            updateEnv("KITE_USER_ID", user.userId);
            System.out.println();
            System.out.println("✓ .env updated. Now run: ./mvnw spring-boot:run");
        } catch (KiteException e) {
            throw new RuntimeException(e);
        }
    }

    private static String env(String key) {
        // First env, then .env file
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) return v;
        try {
            for (String line : Files.readAllLines(Path.of(".env"))) {
                if (line.startsWith(key + "=")) {
                    String val = line.substring(key.length() + 1).trim();
                    if (!val.isBlank()) return val;
                }
            }
        } catch (IOException ignore) {}
        return null;
    }

    /** Replace or append KEY=value in .env. */
    private static void updateEnv(String key, String value) throws IOException {
        Path env = Path.of(".env");
        if (!Files.exists(env)) {
            Files.writeString(env, key + "=" + value + "\n");
            return;
        }
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        Pattern p = Pattern.compile("^" + Pattern.quote(key) + "\\s*=.*$");
        for (String line : Files.readAllLines(env)) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                out.append(key).append('=').append(value).append('\n');
                replaced = true;
            } else {
                out.append(line).append('\n');
            }
        }
        if (!replaced) out.append(key).append('=').append(value).append('\n');
        Path tmp = env.resolveSibling(".env.tmp");
        Files.writeString(tmp, out.toString());
        Files.move(tmp, env, StandardCopyOption.REPLACE_EXISTING);
    }
}
