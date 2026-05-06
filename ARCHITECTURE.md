# AutoScalp — Architecture

## Layers

```
┌───────────────────────────────────────────────────────────────┐
│  Web / Observability                                          │
│  DashboardController · StatusController · /actuator           │
├───────────────────────────────────────────────────────────────┤
│  Orchestration                                                │
│  TradingOrchestrator   ← pulls signals, checks risk, executes │
├───────────────────────────────────────────────────────────────┤
│  Strategy                                                     │
│  StrategyEngine ─► 4 setups ─► ConfluenceGate ─► Confirmation │
│                                                  Gate         │
│  SignalBuffer (cross-instrument best-pick)                    │
├───────────────────────────────────────────────────────────────┤
│  Risk & Safety                                                │
│  RiskManager · SafetyLayer · FlashCrashProtector              │
│  TradingCalendar · TokenHealthMonitor · PositionReconciler    │
│  HeartbeatWatchdog · PidLockfile · StateManager · RateLimiter │
├───────────────────────────────────────────────────────────────┤
│  Execution                                                    │
│  ExecutionEngine (Paper / Live)                               │
│  OptionChainSelector · PositionSizer · BlackScholes           │
├───────────────────────────────────────────────────────────────┤
│  Data                                                         │
│  KiteTickerClient (WS) · PaperDataFeed · DataEngine           │
│  CandleBuilder · CandleSeries · MarketDataBus                 │
├───────────────────────────────────────────────────────────────┤
│  AI Advisor                                                   │
│  ClaudeAdvisor · DayPlanApplier                               │
├───────────────────────────────────────────────────────────────┤
│  Persistence                                                  │
│  TradeJournal (JSON Lines)                                    │
└───────────────────────────────────────────────────────────────┘
```

## Threading model

| Subsystem | Thread origin | Notes |
|---|---|---|
| Kite WebSocket → tick handler | Kite SDK thread | Hot path; must stay <500ms (NFR-001) |
| Candle builders | Same as tick thread | No locks needed; one builder per (instr × interval) |
| Strategy & buffer | `@Scheduled(fixedDelay=5000)` for `drainAndExecute` | Runs on Spring scheduler; `SignalBuffer` synchronized |
| ExitMonitor | `@Scheduled(fixedDelay=1000)` | Polls active trades; uses execution.getLtp |
| HeartbeatWatchdog | `@Scheduled(fixedDelay=10_000)` | Independent of trading hot path |
| TokenHealthMonitor | `@Scheduled(fixedDelay=5min)` | Calls Kite getProfile |
| PositionReconciler | `@Scheduled(fixedDelay=60_000)` | Live mode only |
| Spring Boot 3.3+ virtual threads | enabled in `application.yml` | Web requests + scheduler use Loom |

## Feature coverage matrix

| Spec ID | Feature | Implementation |
|---|---|---|
| F-001 | Real-time tick ingestion | `KiteTickerClient`, `PaperDataFeed` |
| F-002 | 1m / 5m / 15m candles | `CandleBuilder`, `DataEngine` |
| F-003 | 4 setups | `setups/{ORB,VwapPullback,EmaCrossover,VolumeBreakout}Setup` |
| F-004 | Layer 1 confluence (≥2 setups, 5-bar window) | `ConfluenceGate` |
| F-005 | Layer 2 confirmation (≥3-of-4) | `ConfirmationGate` |
| F-006 | Cross-instrument best signal | `SignalBuffer` |
| F-007 | ATR low-percentile filter | `StrategyEngine.volatilityTooLow` |
| F-008 | Option chain selector | `OptionChainSelector` |
| F-009 | LIMIT @ LTP+0.5% | `LiveExecutionEngine.placeBuy` |
| F-010 | 30% / 20% / 10% allocation | `RiskManager.evaluate` |
| F-011 | Exchange SL-M after fill | `LiveExecutionEngine.placeStopLoss`, set in orchestrator |
| F-012 / F-013 | Paper / Live | `PaperExecutionEngine` / `LiveExecutionEngine` (profile-conditional) |
| F-014 / F-015 | 25% SL / 30% TP | `ExitMonitor.tickTrade` |
| F-016 | Trailing SL 45% / 9% | `ExitMonitor` (only moves up) |
| F-017 | IV-aware TP tightening | `IvDetector` + `TradingOrchestrator` |
| F-019 | EOD auto-close 15:25 | `ExitMonitor.eodIfDue` |
| F-020 | **2-SL halt** | `RiskManager.recordExit` + `isHalted` |
| F-021 | 20% profit lock | `RiskManager.recordExit` |
| F-022 | Reduced 20% alloc after 1st SL | `RiskManager.evaluate` |
| F-023 | Gap-day ORB skip (0.5%) | `OpeningRangeBreakoutSetup` |
| F-024 | Recovery trade after 13:30 | `RiskManager.evaluate` |
| F-025 | Max 1 concurrent | Orchestrator: `if (registry.hasActive()) return` |
| F-026 / F-021* | Crash recovery + atomic state | `StateManager` |
| F-027 | PID lockfile | `PidLockfile` |
| F-028 | Token health every 5 min | `TokenHealthMonitor` |
| F-029 | Position reconciliation | `PositionReconciler` |
| F-030 | Flash crash | `FlashCrashProtector` |
| F-031 | Rate limiter (10 req/s) | `RateLimiter` |
| F-032 | Heartbeat watchdog | `HeartbeatWatchdog` |
| F-033 / F-034 | RLock capital + late fill detection | `RiskManager` (ReentrantLock); `LiveExecutionEngine` |
| F-035 | NSE holiday calendar | `TradingCalendar` |
| F-036 | Expiry day handler | `OptionChainSelector.pickExpiry` |
| F-037 | Special events 15% alloc | `RiskManager` + `TradingCalendar` |
| F-041 | Pre-market analyzer | `ClaudeAdvisor.generateDayPlan` |
| F-042 | Post-market reviewer | `ClaudeAdvisor.reviewTrades` |
| F-044 | Day plan applier (no silent fallback) | `DayPlanApplier` |
| F-045 | Web dashboard | `DashboardController` + `dashboard.html` |
| NFR-004 | JSON Lines logging | `TradeJournal` |

## Failure modes & recovery

| Failure | Detection | Recovery |
|---|---|---|
| Kite WS disconnect | `KiteTicker` SDK | `setTryReconnection(true)` + 50 retries (NFR-002) |
| Token expiry | `TokenHealthMonitor` | Mark `tokenValid=false` → safety layer blocks new trades |
| Process crash | systemd / Docker restart policy | `StateManager.load()` on startup + `PositionReconciler` reconciles |
| Duplicate instance | `PidLockfile` | New instance exits with non-zero code |
| Stale day plan | `DayPlanApplier` checks date | Falls back to defaults with warning, never silently uses yesterday's plan |
| Flash crash | `FlashCrashProtector` 60s rolling window | Halts new entries until reset |

## Build artifact

```
target/ai-scalp-trader.jar      ← Spring Boot fat JAR (boot-able)
docker build -t autoscalp:0.1.0 .
```
