CREATE TABLE vendor_profile (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL UNIQUE, -- Added UNIQUE constraint as typically a user has one vendor profile
  name          TEXT NOT NULL,
  legal_type    VARCHAR(32),
  contact_info  JSONB,
  bank_details  JSONB,
  kyc_status    VARCHAR(16),
  created_at    TIMESTAMPTZ DEFAULT now() NOT NULL, -- Added NOT NULL
  updated_at    TIMESTAMPTZ DEFAULT now() NOT NULL  -- Added NOT NULL
);

CREATE TABLE outbox_events (
  id             UUID PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL, -- Added NOT NULL
  aggregate_id   VARCHAR(64) NOT NULL, -- Added NOT NULL
  event_type     VARCHAR(128) NOT NULL, -- Added NOT NULL
  payload        JSONB NOT NULL, -- Added NOT NULL
  processed      BOOLEAN DEFAULT FALSE NOT NULL, -- Added NOT NULL
  created_at     TIMESTAMPTZ DEFAULT now() NOT NULL -- Added NOT NULL
);

-- Optional: Indexes for frequently queried columns
CREATE INDEX IF NOT EXISTS idx_vendor_profile_user_id ON vendor_profile(user_id);
CREATE INDEX IF NOT EXISTS idx_outbox_events_processed_created_at ON outbox_events(processed, created_at);
