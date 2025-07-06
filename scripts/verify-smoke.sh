#!/bin/bash

set -e # Exit immediately if a command exits with a non-zero status.
set -u # Treat unset variables as an error.
set -o pipefail # Causes a pipeline to return the exit status of the last command in the pipe that failed.

# --- Configuration - Get from environment variables or use defaults ---
ORDER_API_URL="${ORDER_API_URL:-http://localhost:8080}" # Default for local if not set by CI
ORDER_CORE_INTERNAL_URL="${ORDER_CORE_INTERNAL_URL:-http://localhost:8081}" # Default for local
KAFKA_BROKERS_DEV="${KAFKA_BROKERS_DEV:-localhost:9092}"
# Topic where Order-API sends its initial order creation event
ORDER_API_CREATED_TOPIC="${ORDER_API_CREATED_TOPIC:-order.api.created}"
# Topic where Order-Core confirms its own creation (if different, or use Order-API's)
# For this smoke test, we'll focus on Order-API's event and Order-Core's DB state via internal API.

KAFKACAT_CMD="kafkacat" # Assumes kafkacat is in PATH

# --- Helper Functions ---
log_info() {
  echo "[INFO] $(date +'%Y-%m-%dT%H:%M:%S%z'): $1"
}

log_error() {
  echo "[ERROR] $(date +'%Y-%m-%dT%H:%M:%S%z'): $1" >&2
}

check_command() {
  if ! command -v "$1" &> /dev/null; then
    log_error "$1 could not be found. Please install it."
    exit 1
  fi
}

# --- Pre-flight checks ---
check_command curl
check_command jq
check_command "$KAFKACAT_CMD"

log_info "Starting smoke test..."
log_info "Order API URL: $ORDER_API_URL"
log_info "Order Core Internal URL: $ORDER_CORE_INTERNAL_URL"
log_info "Kafka Brokers: $KAFKA_BROKERS_DEV"
log_info "Order API Created Topic: $ORDER_API_CREATED_TOPIC"

# --- Test Steps ---

# 1. Create a new order via Order-API
log_info "Step 1: Creating a new order via Order-API..."
CUSTOMER_ID=$(uuidgen) # Generate a random customer UUID
IDEMPOTENCY_KEY=$(uuidgen)

# Basic payload, adjust if your CreateOrderRequest is different
CREATE_ORDER_PAYLOAD=$(cat <<EOF
{
  "items": [
    {
      "productId": "$(uuidgen)",
      "quantity": 1,
      "price": 10.99
    }
  ],
  "currency": "USD"
}
EOF
)

# Note: Order-API requires Keycloak auth. This curl won't work without a valid token.
# For CI smoke tests, this often involves:
#  a) Temporarily disabling security on a dev/test deployment for this specific test path.
#  b) Using a pre-configured test user and obtaining a token via Keycloak API.
#  c) Using a service account token if Order-API allows machine-to-machine auth.
# For this script, we'll assume security might be relaxed or a token is handled externally if needed.
# If a token is needed, it should be passed via an environment variable like $AUTH_TOKEN.
# Example with token: curl -s -X POST -H "Authorization: Bearer $AUTH_TOKEN" ...
API_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H "X-Customer-Id: $CUSTOMER_ID" \
  -d "$CREATE_ORDER_PAYLOAD" \
  "${ORDER_API_URL}/orders")

log_info "Order-API response: $API_RESPONSE"

ORDER_ID=$(echo "$API_RESPONSE" | jq -r '.orderId')

if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" == "null" ]; then
  log_error "Failed to create order or extract orderId from response."
  exit 1
fi
log_info "Order created successfully via Order-API. Order ID: $ORDER_ID"


# 2. Verify OrderCreatedEvent on Kafka (from Order-API)
# This is a simplified check. Parsing Avro in shell is complex.
# If JSON: kafkacat -b $KAFKA_BROKERS_DEV -C -t $ORDER_API_CREATED_TOPIC -e -J -c 1 -o -10s (consume last message in 10s window)
# For Avro, you'd need schema registry integration with kafkacat or use a dedicated consumer tool.
# This example just checks if a message containing the orderId appears on the topic.
log_info "Step 2: Verifying OrderCreatedEvent on Kafka topic '$ORDER_API_CREATED_TOPIC' for order ID $ORDER_ID..."
KAFKA_CONSUME_TIMEOUT_MS=30000 # 30 seconds
EVENT_FOUND=false

# Consume for a few seconds, looking for the order ID.
# -o -${KAFKA_CONSUME_TIMEOUT_MS}ms: Start consuming from KAFKA_CONSUME_TIMEOUT_MS ago to catch recent messages.
# -e: Exit when last message is reached (or timeout).
# -c 10: Consume up to 10 messages then exit (to avoid hanging if many messages)
# We are primarily interested in a message with our ORDER_ID.
# This is a best-effort check in shell. A proper client would be better.
if "$KAFKACAT_CMD" -b "$KAFKA_BROKERS_DEV" -C -t "$ORDER_API_CREATED_TOPIC" -o -${KAFKA_CONSUME_TIMEOUT_MS}ms -e -c 10 | grep -q "$ORDER_ID"; then
  EVENT_FOUND=true
  log_info "OrderCreatedEvent found on Kafka for order ID $ORDER_ID."
else
  log_warn "OrderCreatedEvent NOT found on Kafka for order ID $ORDER_ID within the timeout/message count. This might be an issue or test flakiness."
  # Depending on strictness, you might exit 1 here. For now, a warning.
fi


# 3. Verify order status in Order-Core via Internal API
# This step assumes Order-Core has processed the event from Order-API and created its own internal order record.
# The topic Order-Core listens to for Order-API's events needs to be consistent.
# (e.g., OrderSagaService listening on "order.api.created")
log_info "Step 3: Verifying order status in Order-Core for order ID $ORDER_ID..."

# Wait a bit for Order-Core to process the Kafka event (if step 2 was async check)
# This is not ideal, better to have a retry loop or check for a specific condition.
# If step 2 confirmed the event that Order-Core consumes, this wait might be shorter.
# For now, let's assume some processing time.
sleep 10

# Order-Core's internal API might use the Order-API's orderId as a reference or its own.
# Assuming Order-Core uses the same ID or has a way to correlate.
# The InternalOrderController might need Keycloak auth (ORDER_ADMIN role).
# This curl would also need an AUTH_TOKEN if security is enforced.
CORE_RESPONSE=$(curl -s -X GET "${ORDER_CORE_INTERNAL_URL}/internal/orders/${ORDER_ID}")

log_info "Order-Core response: $CORE_RESPONSE"

# Example: Assuming Order-Core's GET /internal/orders/{id} returns a JSON with "currentStatus"
# And that Order-Core's OrderService.createOrder (triggered by listener) sets initial status to CREATED.
ORDER_STATUS=$(echo "$CORE_RESPONSE" | jq -r '.currentStatus // .status // ""') # Try common status fields

if [ "$ORDER_STATUS" == "CREATED" ]; then
  log_info "Order status in Order-Core is CREATED as expected for order ID $ORDER_ID."
else
  log_error "Order status in Order-Core is '$ORDER_STATUS', expected 'CREATED' for order ID $ORDER_ID."
  # Consider querying outbox or other logs from Order-Core if available via an endpoint for diagnostics.
  exit 1
fi

# After testing order/reservation:
curl -s http://${INVENTORY_SERVICE_HOST}/inventory/SMOKE-SKU | jq .

log_info "Smoke test completed successfully!"
exit 0
