#!/usr/bin/env bash
# Run Ella API k6 tests, save an HTML + JSON report, print clickable links and open the report.
#
#   ./scripts/run.sh user smoke
#   GRAFANA=1 ./scripts/run.sh transaction load
#   NO_OPEN=1 ./scripts/run.sh all smoke        # links only, don't open the browser
set -uo pipefail

API="${1:-all}"
TYPE="${2:-smoke}"
INFLUX_URL="${INFLUX_URL:-http://localhost:8086/k6}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORTS="$ROOT/reports"
mkdir -p "$REPORTS"
cd "$ROOT"   # handleSummary writes reports/... relative to the working directory

case "$API" in
  all) APIS="user product transaction" ;;
  user|product|transaction) APIS="$API" ;;
  *) echo "Unknown API '$API' (user|product|transaction|all)"; exit 2 ;;
esac
case "$TYPE" in smoke|load|stress|soak) ;; *) echo "Unknown type '$TYPE' (smoke|load|stress|soak)"; exit 2 ;; esac

open_url() {
  [ -n "${NO_OPEN:-}" ] && return
  if command -v cmd.exe >/dev/null 2>&1; then cmd.exe /c start "" "$1" >/dev/null 2>&1
  elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$1" >/dev/null 2>&1
  elif command -v open >/dev/null 2>&1; then open "$1"
  fi
}
# file:///C:/... on Windows (Git Bash), file:///home/... elsewhere
file_url() { if command -v cygpath >/dev/null 2>&1; then echo "file:///$(cygpath -m "$1")"; else echo "file://$1"; fi; }

SUMMARY=()
FAILED=0
for a in $APIS; do
  TEST_ID="$a-$TYPE-$(date +%Y%m%d-%H%M%S)"
  HTML="$REPORTS/$TEST_ID.html"
  JSON="$REPORTS/$TEST_ID.json"
  START_MS=$(( $(date +%s) * 1000 - 10000 ))
  ARGS=(run -e "TEST_ID=$TEST_ID")
  [ -n "${BASE_URL:-}" ] && ARGS+=(-e "BASE_URL=$BASE_URL")
  [ -n "${GRAFANA:-}" ] && ARGS+=(--out "influxdb=$INFLUX_URL")

  echo -e "\n=== $TEST_ID ==="
  echo "Live view while running: http://localhost:5665"
  # handleSummary (src/utils/report.js) writes $TEST_ID.html and .json; web dashboard = live view only
  if K6_WEB_DASHBOARD=true k6 "${ARGS[@]}" "$ROOT/tests/$a/$TYPE.js"; then
    STATUS="PASSED"
  else
    STATUS="FAILED (thresholds breached)"; FAILED=1
  fi
  END_MS=$(( $(date +%s) * 1000 + 10000 ))

  LINES="\n$TEST_ID  $STATUS"
  if [ -f "$HTML" ]; then LINES+="\n  HTML report : $(file_url "$HTML")"; open_url "$HTML"
  else LINES+="\n  HTML report : not created (did k6 stop before the end?)"; fi
  [ -f "$JSON" ] && LINES+="\n  JSON summary: $(file_url "$JSON")"
  if [ -n "${GRAFANA:-}" ]; then
    G="$GRAFANA_URL/d/ella-k6-performance?var-testid=$TEST_ID&from=$START_MS&to=$END_MS"
    LINES+="\n  Grafana     : $G"; open_url "$G"
  fi
  SUMMARY+=("$LINES")
done

echo -e "\n==================== REPORTS ===================="
for s in "${SUMMARY[@]}"; do echo -e "$s"; done
echo -e "\nAll reports : $(file_url "$REPORTS")\n"
exit $FAILED
