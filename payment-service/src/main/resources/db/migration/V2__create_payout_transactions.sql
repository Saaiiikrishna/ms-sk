-- V2__create_payout_transactions.sql
CREATE TABLE payout_transactions (
  id                 UUID            PRIMARY KEY,
  payment_id         UUID            NOT NULL REFERENCES payment_transactions(id), -- Foreign key to the original payment
  vendor_id          UUID            NOT NULL, -- Assuming vendor IDs are UUIDs
  gross_amount       NUMERIC(12,2)   NOT NULL,
  commission_amount  NUMERIC(12,2)   NOT NULL,
  net_amount         NUMERIC(12,2)   NOT NULL,
  currency           CHAR(3)         NOT NULL,
  razorpay_payout_id VARCHAR(64)     NULL, -- Nullable, set upon successful Razorpay Payout API call
  status             VARCHAR(32)     NOT NULL,  -- e.g., INIT, PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED
  error_code         VARCHAR(64)     NULL, -- Error code from Razorpay or internal
  error_message      TEXT            NULL, -- Detailed error message
  created_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ     NOT NULL DEFAULT now()
  -- No version column specified in guide, can be added if optimistic locking is desired for this table
  -- version            BIGINT          NOT NULL DEFAULT 0
);

-- Indexes for common query patterns
CREATE INDEX idx_payout_transactions_payment_id ON payout_transactions(payment_id);
CREATE INDEX idx_payout_transactions_vendor_id ON payout_transactions(vendor_id);
CREATE INDEX idx_payout_transactions_status ON payout_transactions(status);
CREATE INDEX idx_payout_transactions_razorpay_payout_id ON payout_transactions(razorpay_payout_id);
