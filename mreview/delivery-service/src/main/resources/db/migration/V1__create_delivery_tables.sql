-- V1: Initial schema for Delivery Service

-- Delivery Profiles Table (Couriers, Vehicles, etc.)
CREATE TABLE delivery_profiles (
  id UUID PRIMARY KEY,
  name TEXT, -- Courier's name or identifier
  phone TEXT, -- Courier's contact phone
  vehicle_info JSONB, -- Details like type, license plate, capacity
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- e.g., ACTIVE, INACTIVE, ON_BREAK
  current_latitude DOUBLE PRECISION,
  current_longitude DOUBLE PRECISION,
  last_seen_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Delivery Assignments Table
CREATE TABLE delivery_assignments (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL, -- Corresponds to the order in Order-Core
  courier_id UUID REFERENCES delivery_profiles(id), -- Assigned courier
  vendor_id UUID NOT NULL, -- ID of the vendor/warehouse for pickup
  customer_id UUID NOT NULL, -- ID of the customer for dropoff

  pickup_address JSONB NOT NULL, -- Could include name, street, city, postal_code, lat, lon
  dropoff_address JSONB NOT NULL, -- Same structure for customer's address

  status VARCHAR(30) NOT NULL, -- e.g., PENDING_ASSIGNMENT, ASSIGNED, ARRIVED_AT_PICKUP, PICKED_UP, IN_TRANSIT, ARRIVED_AT_DROPOFF, DELIVERED, FAILED_DELIVERY, CANCELLED

  estimated_pickup_time TIMESTAMPTZ,
  actual_pickup_time TIMESTAMPTZ,
  estimated_delivery_time TIMESTAMPTZ,
  actual_delivery_time TIMESTAMPTZ,

  pickup_photo_url TEXT,
  delivery_photo_url TEXT,
  pickup_otp_verified BOOLEAN DEFAULT FALSE,
  delivery_otp_verified BOOLEAN DEFAULT FALSE,

  notes TEXT, -- Any special instructions or notes for the delivery

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0 -- For optimistic locking
);

-- Delivery Events Table (History/Audit Log for an assignment)
CREATE TABLE delivery_events (
  id UUID PRIMARY KEY,
  assignment_id UUID NOT NULL REFERENCES delivery_assignments(id) ON DELETE CASCADE,
  event_type VARCHAR(50) NOT NULL, -- e.g., ASSIGNMENT_CREATED, ARRIVED_AT_PICKUP, OTP_VERIFIED, GPS_UPDATE, DELIVERED
  payload JSONB, -- Event-specific details (e.g., GPS coordinates, photo URL, OTP attempt)
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_id UUID, -- ID of the courier or system component that triggered the event
  actor_type VARCHAR(30) -- e.g., 'COURIER', 'SYSTEM', 'CUSTOMER_SERVICE'
);

-- Indexes for common query patterns
CREATE INDEX idx_delivery_profiles_status ON delivery_profiles(status);
CREATE INDEX idx_delivery_assignments_order_id ON delivery_assignments(order_id);
CREATE INDEX idx_delivery_assignments_courier_id ON delivery_assignments(courier_id);
CREATE INDEX idx_delivery_assignments_status ON delivery_assignments(status);
CREATE INDEX idx_delivery_events_assignment_id ON delivery_events(assignment_id);
CREATE INDEX idx_delivery_events_event_type ON delivery_events(event_type);
CREATE INDEX idx_delivery_events_occurred_at ON delivery_events(occurred_at);

-- Optional: Geospatial index if doing proximity searches for couriers
-- CREATE INDEX idx_delivery_profiles_location ON delivery_profiles USING GIST (ST_MakePoint(current_longitude, current_latitude));
-- Requires PostGIS extension: CREATE EXTENSION IF NOT EXISTS postgis;

-- Outbox Events Table for transactional outbox pattern (for Delivery Service)
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL, -- e.g., 'DeliveryAssignment'
  aggregate_id VARCHAR(64) NOT NULL, -- The ID of the aggregate root (e.g., assignment_id)
  event_type VARCHAR(128) NOT NULL, -- e.g., 'delivery.assignment.created', 'delivery.picked_up'
  payload JSONB NOT NULL, -- The event payload as JSON (Avro object converted to JSON for storage)
  processed BOOLEAN NOT NULL DEFAULT FALSE, -- Flag to indicate if the event has been published
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- Optional: for optimistic locking on outbox processing
  -- version BIGINT NOT NULL DEFAULT 0,
  -- Optional: for retry attempts
  -- attempts INT NOT NULL DEFAULT 0,
  -- last_attempt_at TIMESTAMPTZ
  CONSTRAINT uq_delivery_aggregate_event UNIQUE (aggregate_type, aggregate_id, event_type, created_at)
);

CREATE INDEX idx_delivery_outbox_events_processed_created_at ON outbox_events(processed, created_at);
CREATE INDEX idx_delivery_outbox_events_event_type ON outbox_events(event_type);
