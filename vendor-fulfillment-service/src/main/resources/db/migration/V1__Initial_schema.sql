-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Vendor Order Assignment Table
CREATE TABLE vendor_order_assignment (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  order_id     UUID NOT NULL,
  vendor_id    UUID NOT NULL,
  status       VARCHAR(32) NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index on order_id and vendor_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_vendor_order_assignment_order_id ON vendor_order_assignment(order_id);
CREATE INDEX IF NOT EXISTS idx_vendor_order_assignment_vendor_id ON vendor_order_assignment(vendor_id);
CREATE INDEX IF NOT EXISTS idx_vendor_order_assignment_status ON vendor_order_assignment(status);

-- Vendor Order Status History Table
CREATE TABLE vendor_order_status_history (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  assignment_id UUID NOT NULL REFERENCES vendor_order_assignment(id) ON DELETE CASCADE,
  status        VARCHAR(32) NOT NULL,
  occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
  -- Add other audit fields here if needed, e.g., changed_by_user_id UUID
);

CREATE INDEX IF NOT EXISTS idx_vendor_order_status_history_assignment_id ON vendor_order_status_history(assignment_id);

-- Outbox Table for reliable event publishing
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(255) NOT NULL, -- e.g., "VendorOrderAssignment"
    aggregate_id UUID NOT NULL,           -- e.g., VendorOrderAssignment ID
    event_type VARCHAR(255) NOT NULL,     -- e.g., "VendorOrderAssignedEvent"
    payload BYTEA NOT NULL,               -- Avro payload as byte array
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ NULL         -- Timestamp when the event was processed by the poller
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_unprocessed ON outbox_events(processed_at) WHERE processed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_events_aggregate_id ON outbox_events(aggregate_id);

-- Table for inbound event idempotency
CREATE TABLE processed_inbound_events (
    event_id VARCHAR(255) PRIMARY KEY, -- A unique identifier from the incoming event (e.g., message key, or a dedicated event ID field)
    -- Using VARCHAR for flexibility if event IDs are not UUIDs. If they are always UUIDs, UUID type can be used.
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumer_group VARCHAR(255) NOT NULL -- To allow different consumer groups to process the same event if needed, or just for context
);

-- Example: If using Kafka message keys (topic-partition-offset) for idempotency
-- CREATE TABLE processed_inbound_events (
--     topic VARCHAR(255) NOT NULL,
--     partition INTEGER NOT NULL,
--     offset BIGINT NOT NULL,
--     processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
--     consumer_group VARCHAR(255) NOT NULL,
--     PRIMARY KEY (topic, partition, offset, consumer_group)
-- );

-- Add a trigger function to update 'updated_at' timestamp on vendor_order_assignment updates
CREATE OR REPLACE FUNCTION trigger_set_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_timestamp_vendor_order_assignment
BEFORE UPDATE ON vendor_order_assignment
FOR EACH ROW
EXECUTE FUNCTION trigger_set_timestamp();
