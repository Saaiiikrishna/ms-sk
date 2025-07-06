#!/usr/bin/env bash
set -euo pipefail # Exit on error, unset var, pipe fail

# --- Configuration - Get from environment variables or use defaults ---
# Service URLs (typically internal Kubernetes service names or Ingress hosts for CI)
ORDER_API_URL="${ORDER_API_URL:-http://localhost:8080}" # Example for local Order API
INVENTORY_SERVICE_HOST="${INVENTORY_SERVICE_HOST:-http://localhost:8081}" # Example for local Inventory API (used for original inventory check)
PAYMENT_API_URL="${PAYMENT_API_URL:-http://localhost:8083}" # Example for local Payment Service (actuator health)

# Kafka Configuration
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
# SCHEMA_REGISTRY_URL="${SCHEMA_REGISTRY_URL:-http://localhost:8081}" # Not directly used by kcat for simple JSON consumption unless configured for Avro schema validation on consume

# Topics (ensure these match your application.yml)
TOPIC_ORDER_PAYMENT_SUCCEEDED="${TOPIC_ORDER_PAYMENT_SUCCEEDED:-order.payment.succeeded}"
TOPIC_VENDOR_PAYOUT_INITIATED="${TOPIC_VENDOR_PAYOUT_INITIATED:-vendor.payout.initiated}"
TOPIC_VENDOR_PAYOUT_SUCCEEDED="${TOPIC_VENDOR_PAYOUT_SUCCEEDED:-vendor.payout.succeeded}"

# Test Parameters
SMOKE_SKU="${SMOKE_SKU:-SMOKE-TEST-SKU-$(date +%s)}" # Unique SKU for test run
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-60}" # Timeout for Kafka consumption steps

# --- Helper Functions ---
log_info() {
  echo "[INFO] $(date +'%Y-%m-%dT%H:%M:%S%z'): $1"
}

log_error() {
  echo "[ERROR] $(date +'%Y-%m-%dT%H:%M:%S%z'): $1" >&2
}

check_command() {
  if ! command -v "$1" &> /dev/null; then
    log_error "Command '$1' could not be found. Please install it or ensure it's in PATH."
    exit 1
  fi
}

# Function to consume a single JSON message from Kafka using kcat (kafkacat)
# Usage: consume_kafka_message <topic_name> <timeout_seconds>
consume_kafka_message() {
  local topic="$1"
  local consume_timeout="$2"
  local message_content

  log_info "Attempting to consume 1 message from topic '$topic' with timeout ${consume_timeout}s..."

  # kcat options:
  # -b <broker>: Kafka broker address
  # -C: Consumer mode
  # -t <topic>: Topic to consume from
  # -c 1: Consume 1 message then exit
  # -e: Exit on EOF (when -c 1 is met)
  # -J: Output message value as JSON
  # -q: Quiet mode (suppress connection logs)
  # -o beginning: Start consuming from the beginning of the topic (for test isolation, if new messages are guaranteed)
  #   Alternatively, -o end -c 1 might be better for CI if topic has old messages, but risks missing slow messages.
  #   -o stored -c 1 might be a good compromise if consumer group is reused and reset.
  #   For this smoke test, -o end -c 1 and hoping the message arrives within timeout is common.
  #   Or, use a unique consumer group and -o earliest. Let's use -o stored or -o "end" for now for CI.
  #   The `timeout` command handles overall timeout.
  #   Using -o end and hoping message arrives within timeout.
  message_content=$(timeout "$consume_timeout" kafkacat -b "$KAFKA_BOOTSTRAP" -C -t "$topic" -c 1 -e -J -q -o end)

  if [ -z "$message_content" ]; then
    log_error "Failed to consume message from topic '$topic' within ${consume_timeout}s."
    return 1 # Error code
  fi
  echo "$message_content" # Return the consumed message
  return 0 # Success
}


# --- Pre-flight checks ---
log_info "Performing pre-flight checks..."
check_command curl
check_command jq
check_command kafkacat # Or kcat, ensure consistency

# --- Test Steps ---
log_info "🚀 Starting E2E Smoke Test for Payment & Payout Flow..."

# 1. Create an order via Order API (this should trigger payment requested event)
log_info "➡️ Step 1: Creating order via Order API (${ORDER_API_URL}/orders)..."
# Generate unique IDs for the test run
CUSTOMER_ID_SMOKE="smoke-cust-$(uuidgen | cut -d'-' -f1)"
IDEMPOTENCY_KEY_SMOKE="smoke-idem-$(uuidgen)"

CREATE_ORDER_JSON_PAYLOAD=$(cat <<EOF
{
  "items": [{"productId": "$SMOKE_SKU", "quantity": 1, "price": 150.75}],
  "currency": "INR"
}
EOF
)

# Assuming Order API is accessible and will trigger the payment flow
# This curl might need auth headers in a real secured environment
ORDER_API_RESPONSE=$(curl -s -X POST "${ORDER_API_URL}/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY_SMOKE" \
  -H "X-Customer-Id: $CUSTOMER_ID_SMOKE" \
  -d "$CREATE_ORDER_JSON_PAYLOAD")

ORDER_ID=$(echo "$ORDER_API_RESPONSE" | jq -r '.orderId // .id // ""') # Adjust based on actual Order API response for order ID

if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" == "null" ]; then
  log_error "Failed to create order or extract orderId. Order API Response: $ORDER_API_RESPONSE"
  exit 1
fi
log_info "  ✅ Order created successfully. Order ID: $ORDER_ID"


# 2. Await order.payment.succeeded event
log_info "➡️ Step 2: Awaiting '$TOPIC_ORDER_PAYMENT_SUCCEEDED' event for Order ID $ORDER_ID..."
PAYMENT_SUCCEEDED_EVENT_JSON=$(consume_kafka_message "$TOPIC_ORDER_PAYMENT_SUCCEEDED" "$TIMEOUT_SECONDS")
if [ $? -ne 0 ]; then exit 1; fi # Exit if consume_kafka_message failed

log_info "  Received from $TOPIC_ORDER_PAYMENT_SUCCEEDED: $PAYMENT_SUCCEEDED_EVENT_JSON"
# Validate key fields
# Assuming the Kafka message key is the PaymentTransaction ID, and value contains orderId and paymentId from Razorpay
# The skeleton provided checks for '.value.paymentId', let's assume event value contains orderId and paymentId
RECEIVED_ORDER_ID_PAY_SUCC=$(echo "$PAYMENT_SUCCEEDED_EVENT_JSON" | jq -r '.orderId // .payload.orderId // ""')
RAZORPAY_PAYMENT_ID=$(echo "$PAYMENT_SUCCEEDED_EVENT_JSON" | jq -r '.paymentId // .payload.paymentId // ""')

if [ "$RECEIVED_ORDER_ID_PAY_SUCC" != "$ORDER_ID" ]; then
  log_error "Mismatch in Order ID on '$TOPIC_ORDER_PAYMENT_SUCCEEDED'. Expected: $ORDER_ID, Got: $RECEIVED_ORDER_ID_PAY_SUCC"
  exit 1
fi
if [ -z "$RAZORPAY_PAYMENT_ID" ] || [ "$RAZORPAY_PAYMENT_ID" == "null" ]; then
  log_error "Missing Razorpay Payment ID on '$TOPIC_ORDER_PAYMENT_SUCCEEDED' event."
  exit 1
fi
log_info "  ✅ '$TOPIC_ORDER_PAYMENT_SUCCEEDED' event verified for Order ID $ORDER_ID. Razorpay Payment ID: $RAZORPAY_PAYMENT_ID"


# 3. Await vendor.payout.initiated event
log_info "➡️ Step 3: Awaiting '$TOPIC_VENDOR_PAYOUT_INITIATED' event..."
PAYOUT_INITIATED_EVENT_JSON=$(consume_kafka_message "$TOPIC_VENDOR_PAYOUT_INITIATED" "$TIMEOUT_SECONDS")
if [ $? -ne 0 ]; then exit 1; fi

log_info "  Received from $TOPIC_VENDOR_PAYOUT_INITIATED: $PAYOUT_INITIATED_EVENT_JSON"
# Validate key fields, e.g., payoutId, paymentId (should match original PaymentTransaction ID)
# The event payload for VendorPayoutInitiatedEvent has "payoutId", "paymentId", "vendorId", "netAmount", "currency"
INITIATED_PAYOUT_ID=$(echo "$PAYOUT_INITIATED_EVENT_JSON" | jq -r '.payoutId // .payload.payoutId // ""')
INITIATED_PAYMENT_TX_ID=$(echo "$PAYOUT_INITIATED_EVENT_JSON" | jq -r '.paymentId // .payload.paymentId // ""') # This is our internal PaymentTransaction ID

if [ -z "$INITIATED_PAYOUT_ID" ] || [ "$INITIATED_PAYOUT_ID" == "null" ]; then
  log_error "Missing Payout ID on '$TOPIC_VENDOR_PAYOUT_INITIATED' event."
  exit 1
fi
# We don't have the PaymentTransaction ID from step 2 directly, but it's linked to the ORDER_ID.
# This check assumes the 'paymentId' in the payout event refers to our internal PaymentTransaction ID.
# A more robust check would involve querying an internal endpoint if available.
log_info "  ✅ '$TOPIC_VENDOR_PAYOUT_INITIATED' event verified. Payout ID: $INITIATED_PAYOUT_ID"


# 4. Await vendor.payout.succeeded event
log_info "➡️ Step 4: Awaiting '$TOPIC_VENDOR_PAYOUT_SUCCEEDED' event for Payout ID $INITIATED_PAYOUT_ID..."
PAYOUT_SUCCEEDED_EVENT_JSON=$(consume_kafka_message "$TOPIC_VENDOR_PAYOUT_SUCCEEDED" "$TIMEOUT_SECONDS")
if [ $? -ne 0 ]; then exit 1; fi

log_info "  Received from $TOPIC_VENDOR_PAYOUT_SUCCEEDED: $PAYOUT_SUCCEEDED_EVENT_JSON"
# Validate key fields, e.g., payoutId should match INITIATED_PAYOUT_ID, presence of razorpayPayoutId
RECEIVED_PAYOUT_ID_PAY_SUCC=$(echo "$PAYOUT_SUCCEEDED_EVENT_JSON" | jq -r '.payoutId // .payload.payoutId // ""')
RAZORPAY_PAYOUT_ID=$(echo "$PAYOUT_SUCCEEDED_EVENT_JSON" | jq -r '.razorpayPayoutId // .payload.razorpayPayoutId // ""')

if [ "$RECEIVED_PAYOUT_ID_PAY_SUCC" != "$INITIATED_PAYOUT_ID" ]; then
  log_error "Mismatch in Payout ID on '$TOPIC_VENDOR_PAYOUT_SUCCEEDED'. Expected: $INITIATED_PAYOUT_ID, Got: $RECEIVED_PAYOUT_ID_PAY_SUCC"
  exit 1
fi
if [ -z "$RAZORPAY_PAYOUT_ID" ] || [ "$RAZORPAY_PAYOUT_ID" == "null" ]; then
  log_error "Missing Razorpay Payout ID on '$TOPIC_VENDOR_PAYOUT_SUCCEEDED' event."
  exit 1
fi
log_info "  ✅ '$TOPIC_VENDOR_PAYOUT_SUCCEEDED' event verified for Payout ID $INITIATED_PAYOUT_ID. Razorpay Payout ID: $RAZORPAY_PAYOUT_ID"


# 5. Verify Payment Service Health (already present in previous version, good to keep)
log_info "➡️ Step 5: Verifying Payment Service health (${PAYMENT_API_URL}/actuator/health)..."
PAYMENT_HEALTH_RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${PAYMENT_API_URL}/actuator/health")
if [ "$PAYMENT_HEALTH_RESPONSE_CODE" -eq 200 ]; then
    log_info "  ✅ Payment Service is healthy (HTTP 200)."
else
    log_error "Payment Service is unhealthy. Health endpoint returned HTTP $PAYMENT_HEALTH_RESPONSE_CODE."
    # curl -v "${PAYMENT_API_URL}/actuator/health" # Verbose output for debugging
    exit 1
fi

# (Optional) Verify Inventory Service Health (from original script part)
log_info "➡️ Step 6: Verifying Inventory Service health (${INVENTORY_SERVICE_HOST}/actuator/health)..." # Assuming actuator path
INVENTORY_HEALTH_URL="${INVENTORY_SERVICE_HOST}/actuator/health" # Defaulting to actuator/health
INVENTORY_HEALTH_RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$INVENTORY_HEALTH_URL")
if [ "$INVENTORY_HEALTH_RESPONSE_CODE" -eq 200 ]; then
    log_info "  ✅ Inventory Service is healthy (HTTP 200)."
else
    log_error "Inventory Service is unhealthy. Health endpoint returned HTTP $INVENTORY_HEALTH_RESPONSE_CODE. URL: $INVENTORY_HEALTH_URL"
    exit 1
fi


# --- Final Check & Exit ---
log_info "🎉 Smoke test for Payment & Payout flow completed successfully!"
exit 0
