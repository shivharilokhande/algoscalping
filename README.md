# AIScalpTrader

AI-driven scalping bot for Indian equity derivatives (NIFTY/BankNIFTY/FinNIFTY) using Zerodha Kite Connect.

## Tech Stack
- Java 21
- Spring Boot 3.3.5
- Maven (wrapper included)
- Zerodha Kite Connect Java SDK v3.5.0
- Apache Commons Math 3.6.1
- Caffeine cache
- Lombok

## Prerequisites
- JDK 21+
- Zerodha trading account with API access
- (Optional) Anthropic API key for AI advisor

## Build & Run

```bash
# Clone project
cd AIScalpTrader

# Build (Maven wrapper)
./mvnw clean package

# Run (uses application.yml + environment variables)
./mvnw spring-boot:run

# Or run the JAR directly
java -jar target/ai-scalp-trader.jar
```

## Configuration

Main configuration lives in `src/main/resources/application.yml`. Override values via environment variables:

| Variable | Purpose | Default |
|----------|---------|---------|
| `KITE_API_KEY` | Zerodha API key | – |
| `KITE_API_SECRET` | Zerodha API secret | – |
| `KITE_ACCESS_TOKEN` | Daily access token | – |
| `KITE_REQUEST_TOKEN` | Request token (for token refresh) | – |
| `KITE_USER_ID` | Zerodha user ID | – |
| `ANTHROPIC_API_KEY` | Anthropic Claude API key (optional) | – |
| `SERVER_PORT` | HTTP port | `8080` |
| `AUTOSCALP_SYSTEM_MODE` | `PAPER` or `LIVE` | `PAPER` |

Copy `.env.example` to `.env` and fill in your credentials:
```bash
cp .env.example .env
```

## Project Structure
```
src/main/java/com/trading/aiscalptrader/
├── AiScalpTraderApplication.java
├── ai/          # AI advisor (Claude integration)
├── api/         # REST controllers
├── config/       # Spring configuration
├── data/        # Market data models
├── domain/       # Domain entities
├── execution/    # Order execution
├── exit/         # Exit strategies
├── greeks/       # Greek calculations
├── orchestrator/ # Trade orchestration
├── persistence/  # Repositories
├── risk/         # Risk management
├── safety/       # Safety checks
├── strategy/     # Trading strategies
└── tools/        # Utilities (token generator, etc.)
```

## Testing

```bash
# Run unit & integration tests
./mvnw test

# Generate test coverage report (JaCoCo)
./mvnw verify

# Run OWASP dependency vulnerability check
./mvnw verify  # includes dependency-check
```

Target: **80%+ test coverage** for all business logic.

## License

Internal use only — not licensed for public distribution.
# algoscalping
