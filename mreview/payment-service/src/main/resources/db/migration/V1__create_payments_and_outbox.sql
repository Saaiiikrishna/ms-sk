CREATE TABLE payment_transactions (
  id                  UUID PRIMARY KEY,
  order_id            UUID NOT NULL, -- Assuming this correlates to an order ID in your system
  amount              NUMERIC(12,2) NOT NULL,
  currency            CHAR(3) NOT NULL,
  status              VARCHAR(32) NOT NULL, -- e.g., PENDING, SUCCEEDED, FAILED, REFUNDED
  razorpay_order_id   VARCHAR(64) NULL, -- Nullable if payment can fail before Razorpay order creation
  razorpay_payment_id VARCHAR(64) NULL, -- Nullable as it's set upon successful payment/capture
  error_message       TEXT NULL, -- To store any error messages from Razorpay or internal processing
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  version             BIGINT NOT NULL DEFAULT 0
);

-- Index for querying by order_id
CREATE INDEX idx_payment_transactions_order_id ON payment_transactions(order_id);
-- Index for querying by Razorpay IDs (if frequently used for lookups)
CREATE INDEX idx_payment_transactions_razorpay_order_id ON payment_transactions(razorpay_order_id);
CREATE INDEX idx_payment_transactions_razorpay_payment_id ON payment_transactions(razorpay_payment_id);


CREATE TABLE outbox_events (
  id                  UUID PRIMARY KEY,
  aggregate_type      VARCHAR(64) NOT NULL, -- e.g., "Payment"
  aggregate_id        VARCHAR(64) NOT NULL, -- e.g., PaymentTransaction ID or Order ID
  event_type          VARCHAR(128) NOT NULL, -- e.g., "order.payment.succeeded"
  payload             JSONB NOT NULL,
  processed           BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  -- attempt_count    INT NOT NULL DEFAULT 0, -- Optional: for retry logic in poller
  -- last_attempt_at  TIMESTAMPTZ NULL -- Optional: for retry logic
);

-- Index for outbox poller
CREATE INDEX idx_outbox_events_unprocessed ON outbox_events(processed, created_at ASC);
