#!/usr/bin/env bash
# Sends a correctly signed webhook to the local server.
# Usage: ./scripts/send-webhook.sh <event_id> <payment_reference> <SUCCESS|FAILED>
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
SECRET="${WEBHOOK_SECRET:-dev-webhook-secret}"
BODY=$(printf '{"eventId":"%s","paymentReference":"%s","status":"%s"}' "$1" "$2" "$3")
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -hex | sed 's/^.* //')
curl -sS -X POST "$BASE_URL/payments/webhook/" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: $SIG" \
  -d "$BODY"
echo
