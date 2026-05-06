#!/usr/bin/env bash
# Daily Kite access-token refresh.
# Run this every trading day before market open.
#
# Usage: ./scripts/refresh-token.sh
set -e
cd "$(dirname "$0")/.."
./mvnw -q exec:java
