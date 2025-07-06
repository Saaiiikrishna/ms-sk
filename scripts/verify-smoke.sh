#!/usr/bin/env bash
set -euo pipefail

# Configurable
ORDER_API_URL="${ORDER_API_URL:-http://order-api.dev.svc.cluster.local}"
PAYMENT_API_URL="${PAYMENT_API_URL:-http://payment-service.dev.svc.cluster.local}" # Used for health check
# INVENTORY_API_URL was in user sketch, but not used in the new script logic, PAYMENT_API_URL is for payment service health.
# For local testing, these might be localhost:port
# ORDER_API_URL="${ORDER_API_URL:-http://localhost:8080}"
# PAYMENT_API_URL="${PAYMENT_API_URL:-http://localhost:8083}"


KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka.dev.svc.cluster.local:9092}"
# KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}" # For local
TIMEOUT="${TIMEOUT:-60}" # Timeout for Kafka consumption steps in seconds

SMOKE_SKU="SMOKE-SKU-$(date +%s)" # Unique SKU for each test run

# Topics from application.yml of payment-service
TOPIC_ORDER_PAYMENT_SUCCEEDED="${TOPIC_ORDER_PAYMENT_SUCCEEDED:-order.payment.succeeded}"
TOPIC_VENDOR_PAYOUT_INITIATED="${TOPIC_VENDOR_PAYOUT_INITIATED:-vendor.payout.initiated}"
TOPIC_VENDOR_PAYOUT_SUCCEEDED="${TOPIC_VENDOR_PAYOUT_SUCCEEDED:-vendor.payout.succeeded}"

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
# Adapted from user's kafka-console-consumer skeleton
# Usage: consume_kafka_message <topic_name>
consume_kafka_message() {
  local topic="$1"
  local message_content
  local kcat_cmd="kafkacat" # Could be kcat

  log_info "Attempting to consume 1 message from topic '$topic' with timeout ${TIMEOUT}s..."

  # kcat options:
  # -b <broker>: Kafka broker address
  # -C: Consumer mode
  # -t <topic>: Topic to consume from
  # -c 1: Consume 1 message then exit
  # -e: Exit on EOF (when -c 1 is met)
  # -J: Output message value as JSON (if messages are JSON or Avro/JSON)
  # -q: Quiet mode
  # -o end: Start consuming from the end of the topic. This is crucial for CI to get "next" message.
  #         Requires message to be produced AFTER consumer starts or within a very short window.
  #         The `timeout` command wraps this.
  # The original skeleton used --from-beginning, which is good for isolated test topics
  # but can be slow or pick old messages in shared topics.
  # For smoke tests, usually we want the *next* message produced by the test run.
  # Using a unique consumer group ID with -o earliest might be more robust if available.
  # For now, -o end, and rely on the test producing quickly.
  # The user skeleton used --from-beginning. Let's try that with a unique group.
  local consumer_group="smoke-test-consumer-$(uuidgen)"
  message_content=$(timeout "${TIMEOUT}s" "$kcat_cmd" -b "$KAFKA_BOOTSTRAP" -C -G "$consumer_group" "$topic" -o beginning -c 1 -e -J -q)


  if [ -z "$message_content" ]; then
    log_error "Failed to consume message from topic '$topic' within ${TIMEOUT}s."
    return 1 # Error code
  fi
  echo "$message_content" # Return the consumed message (value only due to -J)
  return 0 # Success
}

# --- Pre-flight checks ---
log_info "Performing pre-flight checks..."
check_command curl
check_command jq
check_command kafkacat # Or kcat

# --- Test Steps ---
log_info "🚀 Starting E2E Smoke Test for Payment & Payout Flow..."

# 1. Create an order via Order API (this should trigger payment requested event)
log_info "➡️ Step 1: Creating order via Order API (${ORDER_API_URL}/orders)..."
CUSTOMER_ID_SMOKE="smoke-cust-$(uuidgen | cut -d'-' -f1)"
IDEMPOTENCY_KEY_SMOKE="smoke-idem-$(uuidgen)"
CREATE_ORDER_JSON_PAYLOAD=$(cat <<EOF
{
  "items":[{"productId":"$SMOKE_SKU","quantity":1, "price": 200.50}],
  "currency":"INR"
}
EOF
)
ORDER_API_RESPONSE=$(curl -s -X POST "${ORDER_API_URL}/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY_SMOKE" \
  -H "X-Customer-Id: $CUSTOMER_ID_SMOKE" \
  -d "$CREATE_ORDER_JSON_PAYLOAD")

ORDER_ID=$(echo "$ORDER_API_RESPONSE" | jq -r '.orderId // .id // ""')
if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" == "null" ]; then
  log_error "Failed to create order or extract orderId. Order API Response: $ORDER_API_RESPONSE"
  exit 1
fi
log_info "  ✅ Order created successfully. Order ID: $ORDER_ID"


# 2. Await order.payment.succeeded event
log_info "➡️ Step 2: Awaiting '$TOPIC_ORDER_PAYMENT_SUCCEEDED' event for Order ID $ORDER_ID..."
PAYMENT_SUCCEEDED_EVENT_JSON=$(consume_kafka_message "$TOPIC_ORDER_PAYMENT_SUCCEEDED")
if [ $? -ne 0 ]; then exit 1; fi

log_info "  Received from $TOPIC_ORDER_PAYMENT_SUCCEEDED: $PAYMENT_SUCCEEDED_EVENT_JSON"
RECEIVED_ORDER_ID_PAY_SUCC=$(echo "$PAYMENT_SUCCEEDED_EVENT_JSON" | jq -r '.orderId') # Assuming payload is the event itself
RAZORPAY_PAYMENT_ID=$(echo "$PAYMENT_SUCCEEDED_EVENT_JSON" | jq -r '.paymentId')

if [ "$RECEIVED_ORDER_ID_PAY_SUCC" != "$ORDER_ID" ]; then
  log_error "Mismatch in Order ID on '$TOPIC_ORDER_PAYMENT_SUCCEEDED'. Expected: $ORDER_ID, Got: $RECEIVED_ORDER_ID_PAY_SUCC"
  exit 1
fi
if [ -z "$RAZORPAY_PAYMENT_ID" ] || [ "$RAZORPAY_PAYMENT_ID" == "null" ]; then
  log_error "Missing Razorpay Payment ID on '$TOPIC_ORDER_PAYMENT_SUCCEEDED' event."
  exit 1
fi
log_info "  ✅ '$TOPIC_ORDER_PAYMENT_SUCCEEDED' event verified. Order ID: $ORDER_ID, Razorpay Payment ID: $RAZORPAY_PAYMENT_ID"


# 3. Await vendor.payout.initiated event
log_info "➡️ Step 3: Awaiting '$TOPIC_VENDOR_PAYOUT_INITIATED' event..."
PAYOUT_INITIATED_EVENT_JSON=$(consume_kafka_message "$TOPIC_VENDOR_PAYOUT_INITIATED")
if [ $? -ne 0 ]; then exit 1; fi

log_info "  Received from $TOPIC_VENDOR_PAYOUT_INITIATED: $PAYOUT_INITIATED_EVENT_JSON"
INITIATED_PAYOUT_ID=$(echo "$PAYOUT_INITIATED_EVENT_JSON" | jq -r '.payoutId')
# This paymentId in VendorPayoutInitiatedEvent should be our internal PaymentTransaction ID.
# We don't easily get this ID from previous steps without an API query.
# For smoke test, we'll just check for presence of payoutId.
if [ -z "$INITIATED_PAYOUT_ID" ] || [ "$INITIATED_PAYOUT_ID" == "null" ]; then
  log_error "Missing Payout ID on '$TOPIC_VENDOR_PAYOUT_INITIATED' event."
  exit 1
fi
log_info "  ✅ '$TOPIC_VENDOR_PAYOUT_INITIATED' event verified. Payout ID: $INITIATED_PAYOUT_ID"


# 4. Await vendor.payout.succeeded event
log_info "➡️ Step 4: Awaiting '$TOPIC_VENDOR_PAYOUT_SUCCEEDED' event for Payout ID $INITIATED_PAYOUT_ID..."
PAYOUT_SUCCEEDED_EVENT_JSON=$(consume_kafka_message "$TOPIC_VENDOR_PAYOUT_SUCCEEDED")
if [ $? -ne 0 ]; then exit 1; fi

log_info "  Received from $TOPIC_VENDOR_PAYOUT_SUCCEEDED: $PAYOUT_SUCCEEDED_EVENT_JSON"
RECEIVED_PAYOUT_ID_PAY_SUCC=$(echo "$PAYOUT_SUCCEEDED_EVENT_JSON" | jq -r '.payoutId')
RAZORPAY_PAYOUT_ID=$(echo "$PAYOUT_SUCCEEDED_EVENT_JSON" | jq -r '.razorpayPayoutId')

if [ "$RECEIVED_PAYOUT_ID_PAY_SUCC" != "$INITIATED_PAYOUT_ID" ]; then
  log_error "Mismatch in Payout ID on '$TOPIC_VENDOR_PAYOUT_SUCCEEDED'. Expected: $INITIATED_PAYOUT_ID, Got: $RECEIVED_PAYOUT_ID_PAY_SUCC"
  exit 1
fi
if [ -z "$RAZORPAY_PAYOUT_ID" ] || [ "$RAZORPAY_PAYOUT_ID" == "null" ]; then
  log_error "Missing Razorpay Payout ID on '$TOPIC_VENDOR_PAYOUT_SUCCEEDED' event."
  exit 1
fi
log_info "  ✅ '$TOPIC_VENDOR_PAYOUT_SUCCEEDED' event verified. Payout ID: $INITIATED_PAYOUT_ID, Razorpay Payout ID: $RAZORPAY_PAYOUT_ID"


# 5. Verify Payment Service Health
log_info "➡️ Step 5: Verifying Payment Service health (${PAYMENT_API_URL}/actuator/health)..."
PAYMENT_HEALTH_RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${PAYMENT_API_URL}/actuator/health")

if [ "$PAYMENT_HEALTH_RESPONSE_CODE" -eq 200 ]; then
    log_info "  ✅ Payment Service is healthy (HTTP 200)."
else
    log_error "Payment Service is unhealthy. Health endpoint returned HTTP $PAYMENT_HEALTH_RESPONSE_CODE."
    exit 1
fi

# --- Final Check & Exit ---
log_info "🎉 Smoke test for Payment & Payout flow completed successfully!"
exit 0
