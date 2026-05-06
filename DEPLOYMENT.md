# Deployment Guide

## Local development

```bash
./mvnw spring-boot:run
# http://localhost:8080
```

## Build & test

```bash
./mvnw -B verify         # runs Surefire
./mvnw spring-boot:run   # runs the app on default :8080
```

## Docker

```bash
docker compose up -d
docker compose logs -f autoscalp
```

`./data`, `./logs`, `./models` are mounted as volumes so state survives container restarts.

## Live readiness checklist

- [ ] At least 10 paper sessions reviewed in `logs/risk.json`
- [ ] Kite Connect API subscription active (₹2,000/month)
- [ ] Daily access-token cron in place (token expires at 8:00 AM IST)
- [ ] Heartbeat alert configured to page on ≥60s silence (`data/heartbeat.txt`)
- [ ] Outbound network policy allows `kite.zerodha.com` and `api.anthropic.com`
- [ ] CPU/memory limits set (≤512 MB heap is sufficient)
- [ ] PID lockfile path on persistent volume
- [ ] On-call has direct shell access for `docker exec` if 2-SL halt needs override

## Daily access token refresh

Zerodha access tokens expire daily at 06:00 UTC. Add a cron at 08:00 IST that:

1. Logs into the user's Kite account (manual or via headless browser)
2. Captures the new `access_token`
3. Writes it to the running container's environment via `docker compose up -d --force-recreate` after updating `.env`

## Production systemd unit (without Docker)

```ini
[Unit]
Description=AutoScalp
After=network.target

[Service]
Type=simple
User=autoscalp
WorkingDirectory=/opt/autoscalp
EnvironmentFile=/etc/autoscalp/.env
ExecStart=/usr/lib/jvm/java-21-openjdk/bin/java -Xms256m -Xmx512m -XX:+UseZGC -jar /opt/autoscalp/ai-scalp-trader.jar
Restart=on-failure
RestartSec=10s

[Install]
WantedBy=multi-user.target
```

## Watchdog cron (optional, external)

```bash
*/2 9-15 * * 1-5  /opt/autoscalp/scripts/watchdog.sh || /opt/autoscalp/scripts/page-oncall.sh
```

`watchdog.sh` reads `/opt/autoscalp/data/heartbeat.txt`, fails if older than 60s.

## Switching to LIVE

```bash
# Edit .env
AUTOSCALP_SYSTEM_MODE=LIVE
docker compose up -d --force-recreate
```

The conditional bean wiring auto-selects `LiveExecutionEngine` when mode is `LIVE` and `PaperExecutionEngine` otherwise.

## Rollback

State is in JSON files under `./data`. Roll back by stopping the container, restoring `data/state.json` from backup, and restarting. The orchestrator runs `PositionReconciler` on startup; any divergence with the broker is logged before the first new trade is placed.
