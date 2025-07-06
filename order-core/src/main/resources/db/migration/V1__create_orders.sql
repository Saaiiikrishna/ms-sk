-- V1: Initial schema for core order processing

-- Orders Table
CREATE TABLE orders (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL,
  type VARCHAR(20) NOT NULL, -- e.g., CUSTOMER, RESTOCK
  total_amount NUMERIC(12,2) NOT NULL,
  currency CHAR(3) NOT NULL, -- ISO 4217 currency code
  current_status VARCHAR(30) NOT NULL, -- Current status from OrderStatus enum
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0 -- For optimistic locking
);

-- Order Items Table
CREATE TABLE order_items (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id UUID NOT NULL, -- Changed from product_sku to product_id for consistency if using UUIDs for products
  product_sku VARCHAR(64), -- Kept SKU as well, can be denormalized or primary product identifier
  quantity INT NOT NULL,
  unit_price NUMERIC(12,2) NOT NULL,
  discount NUMERIC(12,2) NOT NULL DEFAULT 0,
  total_price NUMERIC(12,2) NOT NULL -- Typically (unit_price - discount_per_unit) * quantity or (unit_price * quantity) - total_discount
);

-- Order Status History Table
CREATE TABLE order_status_history (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  old_status VARCHAR(30), -- Previous status from OrderStatus enum
  new_status VARCHAR(30) NOT NULL, -- New status from OrderStatus enum
  changed_by VARCHAR(64), -- Identifier of who/what changed the status (e.g., 'customer', 'payment_service', 'user_id')
  timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB -- For any additional context about the status change
);

-- Outbox Events Table for transactional outbox pattern
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL, -- e.g., 'Order', 'Customer'
  aggregate_id VARCHAR(64) NOT NULL, -- The ID of the aggregate root (e.g., order_id)
  event_type VARCHAR(128) NOT NULL, -- e.g., 'order.created', 'order.paid'
  payload JSONB NOT NULL, -- The event payload as JSON
  processed BOOLEAN NOT NULL DEFAULT FALSE, -- Flag to indicate if the event has been published
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- Optional: for optimistic locking on outbox processing
  -- version BIGINT NOT NULL DEFAULT 0,
  -- Optional: for retry attempts
  -- attempts INT NOT NULL DEFAULT 0,
  -- last_attempt_at TIMESTAMPTZ
  CONSTRAINT uq_aggregate_event UNIQUE (aggregate_type, aggregate_id, event_type, created_at) -- Basic uniqueness, more complex needed for idempotency
);

-- Indexes for common query patterns
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_current_status ON orders(current_status);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);
CREATE INDEX idx_outbox_events_processed_created_at ON outbox_events(processed, created_at);
CREATE INDEX idx_outbox_events_event_type ON outbox_events(event_type);
