CREATE TABLE stock_levels (
  sku        VARCHAR(64) PRIMARY KEY,
  available  INT NOT NULL,
  reserved   INT NOT NULL DEFAULT 0,
  version    BIGINT    NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
  id             UUID PRIMARY KEY,
  aggregate_type VARCHAR(64),
  aggregate_id   VARCHAR(64),
  event_type     VARCHAR(128),
  payload        JSONB,
  processed      BOOLEAN DEFAULT FALSE,
  created_at     TIMESTAMPTZ DEFAULT now()
);
