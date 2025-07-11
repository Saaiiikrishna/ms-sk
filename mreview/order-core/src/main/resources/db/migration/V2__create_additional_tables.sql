-- V2: Additional tables for payments, shipments, and returns

-- Payment Transactions Table
CREATE TABLE payment_transactions (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  amount NUMERIC(12,2) NOT NULL,
  currency CHAR(3) NOT NULL, -- ISO 4217 currency code, should match order's currency
  payment_method VARCHAR(32), -- e.g., 'CREDIT_CARD', 'PAYPAL'
  status VARCHAR(32) NOT NULL, -- e.g., 'PENDING', 'SUCCESS', 'FAILED'
  transaction_id VARCHAR(128) UNIQUE, -- ID from the payment gateway
  provider_details JSONB, -- Store additional details from payment provider
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ -- When the payment was confirmed/processed
);

-- Shipments Table
CREATE TABLE shipments (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  order_item_id UUID, -- Optional: if shipment is per item, otherwise for whole order. REFERENCES order_items(id)
  carrier VARCHAR(64),
  tracking_number VARCHAR(128) UNIQUE,
  status VARCHAR(32) NOT NULL, -- e.g., 'PENDING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'FAILED_DELIVERY'
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  shipped_at TIMESTAMPTZ, -- When it was actually shipped
  estimated_delivery_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ, -- When it was actually delivered
  shipping_address JSONB -- Could be denormalized here or referenced if addresses are separate entities
);

-- Return Requests Table
CREATE TABLE return_requests (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  order_item_id UUID NOT NULL REFERENCES order_items(id) ON DELETE CASCADE, -- Return is typically per item
  quantity INT NOT NULL,
  reason TEXT,
  status VARCHAR(32) NOT NULL, -- e.g., 'REQUESTED', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED'
  rma_number VARCHAR(64) UNIQUE, -- Return Merchandise Authorization
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), -- When the return was requested
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), -- Last update to the return request
  processed_at TIMESTAMPTZ, -- When the return was fully processed (e.g., refunded/credited)
  resolution VARCHAR(32) -- e.g., 'REFUND', 'STORE_CREDIT', 'REPLACEMENT'
);

-- Indexes for new tables
CREATE INDEX idx_payment_transactions_order_id ON payment_transactions(order_id);
CREATE INDEX idx_payment_transactions_transaction_id ON payment_transactions(transaction_id);
CREATE INDEX idx_shipments_order_id ON shipments(order_id);
CREATE INDEX idx_shipments_tracking_number ON shipments(tracking_number);
CREATE INDEX idx_return_requests_order_id ON return_requests(order_id);
CREATE INDEX idx_return_requests_order_item_id ON return_requests(order_item_id);
CREATE INDEX idx_return_requests_rma_number ON return_requests(rma_number);
